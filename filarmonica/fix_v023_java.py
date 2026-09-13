from pathlib import Path

p = Path('app/src/main/java/ro/filarmonica/transilvania/stagiune/FinalMainActivity.java')
s = p.read_text(encoding='utf-8')

fixes = {
    '    private int dp(int v)    private int dp(int v)': '    private int dp(int v)',
    '    private void updateSyncFromStatus(JSONObject st)    private void updateSyncFromStatus(JSONObject st)': '    private void updateSyncFromStatus(JSONObject st)',
}

for old, new in fixes.items():
    if old not in s:
        raise SystemExit(f'v0.23 Java cleanup marker missing: {old}')
    s = s.replace(old, new, 1)

p.write_text(s, encoding='utf-8')
print('v0.23 generated Java duplicate signatures fixed')
