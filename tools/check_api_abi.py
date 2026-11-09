"""Check the public JVM contract across the Android wrapper/core split (stdlib only)."""
import argparse
import io
from pathlib import Path
import re
import struct
import sys
import xml.etree.ElementTree as ET
import zipfile


def class_contract(data):
    source = io.BytesIO(data)
    read = source.read
    u1 = lambda: struct.unpack(">B", read(1))[0]
    u2 = lambda: struct.unpack(">H", read(2))[0]
    u4 = lambda: struct.unpack(">I", read(4))[0]
    if u4() != 0xCAFEBABE:
        raise ValueError("Invalid class file")
    read(4)
    pool = [None] * u2()
    index = 1
    while index < len(pool):
        tag = u1()
        if tag == 1:
            pool[index] = read(u2()).decode("utf-8", "replace")
        elif tag == 7:
            pool[index] = ("class", u2())
        elif tag == 12:
            pool[index] = ("descriptor", u2(), u2())
        elif tag == 16:
            pool[index] = ("method_type", u2())
        elif tag in (3, 4, 9, 10, 11, 17, 18):
            read(4)
        elif tag in (5, 6):
            read(8)
            index += 1
        elif tag in (8, 19, 20):
            read(2)
        elif tag == 15:
            read(3)
        else:
            raise ValueError(f"Unknown constant-pool tag {tag}")
        index += 1
    class_name = lambda value: pool[pool[value][1]] if value else ""
    flags, name, parent = u2(), class_name(u2()), class_name(u2())
    interfaces = tuple(class_name(u2()) for _ in range(u2()))
    references = []
    for entry in pool:
        if isinstance(entry, tuple):
            if entry[0] in ("class", "method_type"):
                references.append(pool[entry[1]])
            elif entry[0] == "descriptor":
                references.append(pool[entry[2]])

    read2 = lambda stream: struct.unpack(">H", stream.read(2))[0]
    read4 = lambda stream: struct.unpack(">I", stream.read(4))[0]

    def annotation(stream):
        references.append(pool[read2(stream)])
        for _ in range(read2(stream)):
            stream.read(2)  # element name
            element_value(stream)

    def element_value(stream):
        tag = stream.read(1)
        if tag in (b"B", b"C", b"D", b"F", b"I", b"J", b"S", b"Z", b"s"):
            stream.read(2)  # primitives and strings are not type references
        elif tag == b"e":
            references.append(pool[read2(stream)])
            stream.read(2)
        elif tag == b"c":
            references.append(pool[read2(stream)])
        elif tag == b"@":
            annotation(stream)
        elif tag == b"[":
            for _ in range(read2(stream)):
                element_value(stream)
        else:
            raise ValueError(f"Unknown annotation element tag {tag!r}")

    def attributes(stream):
        nonlocal flags
        for _ in range(read2(stream)):
            attr_name = pool[read2(stream)]
            payload = io.BytesIO(stream.read(read4(stream)))
            if attr_name == "Signature":
                references.append(pool[read2(payload)])
            elif attr_name == "InnerClasses":
                for _ in range(read2(payload)):
                    inner, _, _, access = struct.unpack(">HHHH", payload.read(8))
                    if class_name(inner) == name:
                        # Source visibility/static membership live here, not in access_flags.
                        flags = (flags & ~0xF) | (access & 0xF)
            elif attr_name in ("RuntimeVisibleAnnotations", "RuntimeInvisibleAnnotations"):
                for _ in range(read2(payload)):
                    annotation(payload)
            elif attr_name in ("RuntimeVisibleParameterAnnotations", "RuntimeInvisibleParameterAnnotations"):
                for _ in range(payload.read(1)[0]):
                    for _ in range(read2(payload)):
                        annotation(payload)
            elif attr_name == "AnnotationDefault":
                element_value(payload)
            elif attr_name in ("RuntimeVisibleTypeAnnotations", "RuntimeInvisibleTypeAnnotations"):
                for _ in range(read2(payload)):
                    target = payload.read(1)[0]
                    if target in (0x40, 0x41):
                        payload.read(read2(payload) * 6)
                    else:
                        sizes = {0x00: 1, 0x01: 1, 0x10: 2, 0x11: 2, 0x12: 2, 0x13: 0,
                                 0x14: 0, 0x15: 0, 0x16: 1, 0x17: 2, 0x42: 2,
                                 0x43: 2, 0x44: 2, 0x45: 2, 0x46: 2,
                                 0x47: 3, 0x48: 3, 0x49: 3, 0x4A: 3, 0x4B: 3}
                        payload.read(sizes[target])
                    payload.read(payload.read(1)[0] * 2)  # type_path
                    annotation(payload)
            elif attr_name == "Code":
                payload.read(4)  # max_stack, max_locals
                payload.read(read4(payload))
                payload.read(read2(payload) * 8)  # exception table
                attributes(payload)
            elif attr_name in ("LocalVariableTable", "LocalVariableTypeTable"):
                for _ in range(read2(payload)):
                    _, _, _, descriptor, _ = struct.unpack(">HHHHH", payload.read(10))
                    references.append(pool[descriptor])

    members = {}
    for kind in ("field", "method"):
        for _ in range(u2()):
            access, member_name, descriptor = u2(), pool[u2()], pool[u2()]
            references.append(descriptor)
            if access & 5:  # public or protected, including synthetic JVM entry points
                members[(kind, member_name, descriptor)] = access
            attributes(source)
    attributes(source)
    return name, (flags, parent, interfaces, members), references


def contracts(path):
    with zipfile.ZipFile(path) as artifact:
        if path.suffix == ".aar":
            with zipfile.ZipFile(io.BytesIO(artifact.read("classes.jar"))) as classes:
                entries = [(name, classes.read(name)) for name in classes.namelist() if name.endswith(".class")]
        else:
            entries = [(name, artifact.read(name)) for name in artifact.namelist() if name.endswith(".class")]
    result, android = {}, []
    for _, data in entries:
        name, contract, references = class_contract(data)
        if name.startswith("io/grimoire/api/"):
            result[name] = contract
        if any(value.startswith("android/") or re.search(r"Landroid/[^;<>]+", value) for value in references):
            android.append(name)
    return result, android


def check(baseline, current, core, android_pom=None, core_pom=None):
    old = []
    for path in baseline:
        previous, _ = contracts(path)
        old.extend(previous.items())
    combined, problems = {}, []
    for path in current:
        artifact, android = contracts(path)
        if path == core and android:
            problems.extend(f"Android reference in JVM core: {name}" for name in android)
        for name, contract in artifact.items():
            if name in combined:
                problems.append(f"Duplicate class: {name}")
            combined[name] = contract
    checked = 0
    for name, previous in old:
        flags, parent, interfaces, members = previous
        if not flags & 5 or flags & 0x1000:  # skip private/package implementation classes and synthetic lambdas
            continue
        checked += 1
        updated = combined.get(name)
        if updated is None:
            problems.append(f"Missing class: {name}")
            continue
        if updated[0] & 0x663F != flags & 0x663F or updated[1:3] != (parent, interfaces):
            problems.append(f"Changed class hierarchy/access: {name}")
        for symbol, access in members.items():
            replacement = updated[3].get(symbol)
            if replacement is None or replacement & 0x41D != access & 0x41D:
                problems.append(f"Changed/missing member: {name} {symbol}")
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    if android_pom and core_pom:
        wrapper = ET.parse(android_pom).getroot()
        portable = ET.parse(core_pom).getroot()
        version = wrapper.findtext("m:version", namespaces=ns)
        dependencies = wrapper.findall("m:dependencies/m:dependency", ns)
        exported = [item for item in dependencies if item.findtext("m:groupId", namespaces=ns) == "io.grimoire"
                    and item.findtext("m:artifactId", namespaces=ns) == "extensions-core"]
        if len(exported) != 1 or exported[0].findtext("m:version", namespaces=ns) != version or exported[0].findtext("m:scope", namespaces=ns) != "compile":
            problems.append("Android POM must re-export the same core version at compile scope")
        if portable.findtext("m:version", namespaces=ns) != version or portable.findtext("m:artifactId", namespaces=ns) != "extensions-core":
            problems.append("Core publication must use the matching version and coordinate")
    return checked, problems


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", type=Path, nargs="+", required=True)
    parser.add_argument("--current", type=Path, nargs="+", required=True)
    parser.add_argument("--core", type=Path, required=True)
    parser.add_argument("--android-pom", type=Path)
    parser.add_argument("--core-pom", type=Path)
    args = parser.parse_args()
    if args.core not in args.current:
        parser.error("--core must also be present in --current")
    count, problems = check(args.baseline, args.current, args.core, args.android_pom, args.core_pom)
    if problems:
        print("\n".join(problems), file=sys.stderr)
        return 1
    print(f"PASS: {count} public/protected classes retain JVM descriptors; no duplicates or Android references in core")
    return 0


if __name__ == "__main__":
    sys.exit(main())
