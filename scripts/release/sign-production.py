"""Sign and verify the two production APKs without exposing signing secrets to Gradle."""

import base64
import hashlib
import json
import os
import re
import subprocess
import tempfile
from pathlib import Path


def main():
    version = os.environ["RELEASE_VERSION"]
    code = os.environ["RELEASE_VERSION_CODE"]
    sdk = Path(os.environ["ANDROID_HOME"]) / "build-tools/37.0.0"
    expected = os.environ["DHMO_PRODUCTION_CERT_SHA256"].lower()
    if not re.fullmatch(r"[0-9a-f]{64}", expected):
        raise ValueError("Configure the production environment's DHMO_PRODUCTION_CERT_SHA256 variable")
    output = Path("dist")
    output.mkdir()
    with tempfile.TemporaryDirectory(prefix="dhmo-production-", dir=os.environ["RUNNER_TEMP"]) as directory:
        key = Path(directory) / "production.p12"
        key.write_bytes(base64.b64decode(os.environ["DHMO_PRODUCTION_KEYSTORE_BASE64"], validate=True))
        key.chmod(0o600)
        cert = subprocess.check_output([
            "keytool", "-exportcert", "-keystore", str(key),
            "-storepass:env", "DHMO_PRODUCTION_KEYSTORE_PASSWORD",
            "-alias", os.environ["DHMO_PRODUCTION_KEY_ALIAS"],
        ])
        if hashlib.sha256(cert).hexdigest() != expected:
            raise ValueError("The production keystore certificate does not match its pinned fingerprint")
        for module in ["app", "wear"]:
            apk = Path("unsigned") / f"dhmo-{module}-{version}-unsigned.apk"
            badging = subprocess.check_output([str(sdk / "aapt"), "dump", "badging", str(apk)], text=True)
            package = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
            if not package or package.groups() != ("casa.peterson.dhmo", code, version):
                raise ValueError(f"Incorrect production package or version metadata: {apk}")
            if "application-debuggable" in badging or "application-testOnly" in badging:
                raise ValueError(f"Refusing to production-sign a debuggable/test-only APK: {apk}")
            signed = output / f"dhmo-{module}-{version}.apk"
            subprocess.run([
                str(sdk / "apksigner"), "sign", "--ks", str(key),
                "--ks-key-alias", os.environ["DHMO_PRODUCTION_KEY_ALIAS"],
                "--ks-pass", "env:DHMO_PRODUCTION_KEYSTORE_PASSWORD",
                "--key-pass", "env:DHMO_PRODUCTION_KEY_PASSWORD",
                "--debuggable-apk-permitted", "false", "--out", str(signed), str(apk),
            ], check=True)
            verification = subprocess.check_output([str(sdk / "apksigner"), "verify", "--print-certs", str(signed)], text=True)
            digests = re.findall(r"certificate SHA-256 digest: ([0-9a-f]+)", verification)
            if digests != [expected]:
                raise ValueError(f"Unexpected production APK signer: {signed}")
            print(f"Verified {signed}: production package, version, non-debuggable, certificate {expected}")
    manifest = {
        "source_debug_tag": os.environ["SOURCE_DEBUG_TAG"], "source_commit": os.environ["SOURCE_SHA"],
        "version_name": version, "version_code": int(code), "certificate_sha256": expected,
        "apks": {apk.name: hashlib.sha256(apk.read_bytes()).hexdigest() for apk in output.glob("*.apk")},
    }
    (output / "promotion.json").write_text(json.dumps(manifest, indent=2) + "\n")


if __name__ == "__main__":
    main()
