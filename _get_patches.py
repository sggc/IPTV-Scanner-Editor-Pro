import urllib.request, json, time

# CAVS-related commits (chronological order, oldest first)
cavs_shas = [
    'd208cf8',  # add cavs, dra decoder
    '48ad875',  # interlaced frame default top field first
    'e633613',  # set frame correct pts and flags
    'fb9a3ec',  # reset decoder when flush
    '0de8ee1',  # modify cavs long name
    '4cd3e6d',  # init_crop_table() func to static
]

# Get patch for each commit
for sha in cavs_shas:
    url = f'https://github.com/llawsxx/FFmpeg/commit/{sha}.patch'
    req = urllib.request.Request(url, headers={'Accept': 'text/plain'})
    with urllib.request.urlopen(req) as resp:
        patch = resp.read().decode('utf-8', errors='replace')
    fname = f'd:/github/IPTV-Scanner-Editor-Pro/_cavs_patch_{sha}.patch'
    with open(fname, 'w', encoding='utf-8') as f:
        f.write(patch)
    # Print summary
    lines = patch.splitlines()
    print(f'=== {sha} ({len(patch)} bytes, {len(lines)} lines) ===')
    # Print files affected
    for line in lines:
        if line.startswith('diff --git'):
            print(f'  {line}')
    time.sleep(0.5)

print()
print('Patches saved to _cavs_patch_*.patch files')
