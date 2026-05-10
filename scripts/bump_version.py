import re
import sys
import argparse

def bump_version(file_path, bump_type):
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # Find versionCode
    code_match = re.search(r'versionCode\s*=\s*(\d+)', content)
    if not code_match:
        # Try Groovy style just in case
        code_match = re.search(r'versionCode\s+(\d+)', content)

    if not code_match:
        print("Could not find versionCode")
        sys.exit(1)

    old_code = int(code_match.group(1))
    new_code = old_code + 1

    # Find versionName
    name_match = re.search(r'versionName\s*=\s*"(.*?)"', content)
    if not name_match:
        # Try Groovy style just in case
        name_match = re.search(r'versionName\s+"(.*?)"', content)

    if not name_match:
        print("Could not find versionName")
        sys.exit(1)

    old_name = name_match.group(1)
    parts = old_name.split('.')

    # Standardize to 3 parts if needed for semver bump
    while len(parts) < 3:
        parts.append('0')

    major, minor, patch = map(int, parts[:3])

    if bump_type == 'major':
        major += 1
        minor = 0
        patch = 0
    elif bump_type == 'minor':
        minor += 1
        patch = 0
    elif bump_type == 'patch':
        patch += 1

    new_name = f"{major}.{minor}.{patch}"

    # Replace in content
    content = content.replace(code_match.group(0), f"versionCode = {new_code}")
    content = content.replace(name_match.group(0), f'versionName = "{new_name}"')

    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)

    print(f"Bumped {old_name} ({old_code}) -> {new_name} ({new_code})")
    return new_name

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--file", required=True)
    parser.add_argument("--bump", choices=['major', 'minor', 'patch'], default='patch')
    args = parser.parse_args()

    new_ver = bump_version(args.file, args.bump)
    print(f"::set-output name=new_version::{new_ver}")
    # For modern GitHub Actions
    with open(sys.argv[0].replace('bump_version.py', 'version.txt'), 'w') as f:
        f.write(new_ver)
