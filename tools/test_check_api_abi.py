"""Compile real JVM fixtures to verify the artifact compatibility gate."""
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path
import zipfile

from check_api_abi import check, contracts


class AbiGateTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def artifact(self, name, files):
        root = self.root / name
        source, classes = root / "src", root / "classes"
        classes.mkdir(parents=True)
        paths = []
        for filename, body in files.items():
            path = source / filename
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(body, encoding="utf-8")
            paths.append(str(path))
        subprocess.run([shutil.which("javac") or "javac", "--release", "11", "-d", str(classes), *paths],
                       check=True, capture_output=True)
        jar = root / "fixture.jar"
        with zipfile.ZipFile(jar, "w") as archive:
            for path in classes.rglob("*.class"):
                if not path.relative_to(classes).as_posix().startswith("android/"):
                    archive.write(path, path.relative_to(classes).as_posix())
        return jar

    def test_android_strings_are_not_type_references(self):
        jar = self.artifact("strings", {"io/grimoire/api/Note.java":
            "package io.grimoire.api; public @interface Note { String value(); }",
            "io/grimoire/api/Message.java": '''
            package io.grimoire.api;
            @Note("android.webkit.CookieManager")
            public class Message {
                public static final String URL = "https://example.com/android/help";
                public String text() { return "android.webkit.CookieManager"; }
            }'''})
        self.assertEqual([], contracts(jar)[1])

    def test_android_types_are_detected_in_members_arrays_generics_and_bytecode(self):
        stub = "package android.webkit; public class Browser {}"
        for name, member in {
            "Member": "private android.webkit.Browser browser;",
            "Array": "public android.webkit.Browser[] browsers() { return null; }",
            "Generic": "public java.util.List<android.webkit.Browser> browsers;",
            "Code": "public Object browser() { return new android.webkit.Browser(); }",
        }.items():
            with self.subTest(name=name):
                jar = self.artifact(name, {"android/webkit/Browser.java": stub,
                    f"io/grimoire/api/{name}.java": f"package io.grimoire.api; public class {name} {{ {member} }}"})
                self.assertIn(f"io/grimoire/api/{name}", contracts(jar)[1])

    def test_android_annotation_types_are_detected_without_treating_strings_as_types(self):
        jar = self.artifact("annotations", {
            "android/webkit/Mark.java": "package android.webkit; public @interface Mark {}",
            "io/grimoire/api/Marked.java": "package io.grimoire.api; @android.webkit.Mark public class Marked {}",
        })
        self.assertIn("io/grimoire/api/Marked", contracts(jar)[1])

    def test_every_overlapping_baseline_contract_is_checked_in_both_orders(self):
        first = self.artifact("first", {"io/grimoire/api/Source.java":
            "package io.grimoire.api; public class Source { public void before() {} }"})
        second = self.artifact("second", {"io/grimoire/api/Source.java":
            "package io.grimoire.api; public class Source { public void after() {} }"})
        for baseline in ([first, second], [second, first]):
            with self.subTest(baseline=baseline):
                _, problems = check(baseline, [second], second)
                self.assertTrue(any("before" in p for p in problems), problems)

    def test_protected_nested_contracts_and_visibility_are_preserved(self):
        before = self.artifact("nested-before", {"io/grimoire/api/Outer.java":
            "package io.grimoire.api; public class Outer { protected static class Nested { public void method() {} } }"})
        self.assertEqual(4, contracts(before)[0]["io/grimoire/api/Outer$Nested"][0] & 7)
        for name, nested in {"missing": "", "changed": "protected static class Nested {}",
                             "private": "private static class Nested { public void method() {} }"}.items():
            with self.subTest(name=name):
                after = self.artifact(name, {"io/grimoire/api/Outer.java":
                    f"package io.grimoire.api; public class Outer {{ {nested} }}"})
                _, problems = check([before], [after], after)
                self.assertTrue(any("Outer$Nested" in p for p in problems), problems)

    def test_annotation_class_kind_cannot_change(self):
        annotation = self.artifact("annotation", {"io/grimoire/api/Info.java":
            "package io.grimoire.api; public @interface Info { String name(); }"})
        interface = self.artifact("interface", {"io/grimoire/api/Info.java":
            "package io.grimoire.api; public interface Info extends java.lang.annotation.Annotation { String name(); }"})
        _, problems = check([annotation], [interface], interface)
        self.assertTrue(any("hierarchy/access" in p for p in problems), problems)


if __name__ == "__main__":
    unittest.main()
