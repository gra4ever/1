from pathlib import Path

p = Path('prepare_v023.py')
s = p.read_text(encoding='utf-8')
start = s.find('# Four navigation destinations; Settings hosts the notification master switch.')
end = s.find('# Search: show production week first', start)
if start < 0 or end < 0:
    raise SystemExit('v0.23 fixer: navigation block markers not found')
replacement = '''# Four navigation destinations; Settings hosts the notification master switch.
nav_marker = '        addNav("⌕\\nSearch", "Search".equals(selected), this::showSearch);'
if nav_marker not in text:
    raise SystemExit('v0.23 patch failed: Search navigation marker not found')
text = text.replace(
    nav_marker,
    '        addNav("⌕\\nCaută", "Search".equals(selected), this::showSearch);\\n'
    '        addNav("⚙\\nSetări", "Setări".equals(selected), this::showSettings);',
    1,
)

'''
s = s[:start] + replacement + s[end:]
p.write_text(s, encoding='utf-8')
print('prepare_v023 navigation matcher fixed')
