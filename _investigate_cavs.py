import urllib.request, json, time

# Get CAVS-related commits from llawsxx/FFmpeg
all_commits = []
for page in range(1, 5):
    url = f'https://api.github.com/repos/llawsxx/FFmpeg/commits?per_page=100&page={page}'
    with urllib.request.urlopen(url) as resp:
        commits = json.loads(resp.read())
        if not commits:
            break
        all_commits.extend(commits)
    time.sleep(0.5)

# Filter CAVS-related commits
keywords = ['cavs', 'libcavs', 'guangdian', 'jizhun', 'avs1']
cavs_commits = []
for c in all_commits:
    msg = c['commit']['message'].lower()
    if any(k in msg for k in keywords):
        cavs_commits.append(c)

print(f'Total commits: {len(all_commits)}, CAVS-related: {len(cavs_commits)}')
print('=== CAVS-related commits ===')
for c in cavs_commits:
    sha = c['sha'][:7]
    msg = c['commit']['message'].splitlines()[0]
    print(f'{sha} | {msg}')

# Get files modified by each CAVS commit
print()
print('=== Files modified by CAVS commits ===')
all_files = set()
for c in cavs_commits:
    sha = c['sha']
    url = f'https://api.github.com/repos/llawsxx/FFmpeg/commits/{sha}'
    with urllib.request.urlopen(url) as resp:
        detail = json.loads(resp.read())
    time.sleep(0.5)
    files = [f['filename'] for f in detail['files']]
    all_files.update(files)
    print(f'{sha[:7]}: {files}')

print()
print(f'=== All CAVS-related files ({len(all_files)}) ===')
for f in sorted(all_files):
    print(f)
