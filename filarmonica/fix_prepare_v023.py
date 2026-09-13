from pathlib import Path

p = Path('prepare_v023.py')
s = p.read_text(encoding='utf-8')
start = s.find('# Four navigation destinations; Settings hosts the notification master switch.')
end = s.find('# Search: show production week first', start)
if start < 0 or end < 0:
    raise SystemExit('v0.23 fixer: navigation block markers not found')
replacement = '''# Four navigation destinations; Settings hosts the notification master switch.
nav_marker = r'        addNav("⌕\\nSearch", "Search".equals(selected), this::showSearch);'
if nav_marker not in text:
    raise SystemExit('v0.23 patch failed: Search navigation marker not found')
nav_replacement = (
    r'        addNav("⌕\\nCaută", "Search".equals(selected), this::showSearch);'
    + '\\n' +
    r'        addNav("⚙\\nSetări", "Setări".equals(selected), this::showSettings);'
)
text = text.replace(nav_marker, nav_replacement, 1)

'''
s = s[:start] + replacement + s[end:]
p.write_text(s, encoding='utf-8')
print('prepare_v023 navigation matcher fixed')
