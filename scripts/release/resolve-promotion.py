"""Validate a manual promotion and resolve a published debug tag to its exact commit."""

import json
import os
import re
import subprocess
from pathlib import Path


def api(endpoint):
    return json.loads(subprocess.check_output(["gh", "api", endpoint], text=True))


def main():
    repo = os.environ["GITHUB_REPOSITORY"]
    source = os.environ["SOURCE_DEBUG_TAG"]
    version = os.environ["RELEASE_VERSION"]
    code = os.environ["RELEASE_VERSION_CODE"]
    if not re.fullmatch(r"debug-[1-9][0-9]*-[0-9a-f]{7}", source):
        raise ValueError("Choose a published debug-N-abcdef0 release tag")
    if not re.fullmatch(r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)", version):
        raise ValueError("Version must be a release number such as 1.2.3")
    if not re.fullmatch(r"[1-9][0-9]*", code) or int(code) > 2100000000:
        raise ValueError("Android version code must be an integer from 1 to 2100000000")
    release = api(f"repos/{repo}/releases/tags/{source}")
    if release["draft"] or not release["prerelease"]:
        raise ValueError("The source must be a published debug pre-release")
    names = [asset["name"] for asset in release["assets"]]
    suffix = source.removeprefix("debug-")
    for module in ["app", "wear"]:
        if names.count(f"dhmo-{module}-debug-{suffix}.apk") != 1:
            raise ValueError(f"The source release needs exactly one {module} debug APK")
    sha = api(f"repos/{repo}/commits/{source}")["sha"]
    if not sha.startswith(source.rsplit("-", 1)[1]):
        raise ValueError("The debug release tag does not match its source commit")
    if api(f"repos/{repo}/compare/main...{sha}")["status"] not in ["behind", "identical"]:
        raise ValueError("The selected debug build must come from main's history")
    # Do not overwrite an existing tag, even if no release has been published for it.
    tag = f"v{version}"
    tags = subprocess.check_output(
        ["gh", "api", "--paginate", f"repos/{repo}/tags?per_page=100", "--jq", ".[].name"], text=True
    ).splitlines()
    if tag in tags:
        raise ValueError(f"Tag {tag} already exists; choose a new version")
    releases = subprocess.check_output(
        ["gh", "api", "--paginate", f"repos/{repo}/releases?per_page=100", "--jq", ".[] | @json"], text=True
    ).splitlines()
    for line in releases:
        previous = json.loads(line)
        if previous["tag_name"] == tag:
            raise ValueError(f"Release {tag} already exists")
        if previous["draft"] or previous["prerelease"]:
            continue
        match = re.search(r"^Android version code: ([0-9]+)$", previous.get("body") or "", re.MULTILINE)
        if match and int(code) <= int(match[1]):
            raise ValueError("Android version code must exceed every previously promoted release")
    with Path(os.environ["GITHUB_OUTPUT"]).open("a") as output:
        output.write(f"sha={sha}\ntag={tag}\nversion={version}\nversion_code={code}\nsource={source}\n")
    print(f"Promoting {source} ({sha}) to {tag}, Android version code {code}")


if __name__ == "__main__":
    main()
