from pathlib import Path

p = Path('prepare_v023.py')
s = p.read_text(encoding='utf-8')
start = s.find('# Four navigation destinations; Settings hosts the notification master switch.')
end = s.find('# Search: show production week first', start)
if start < 0 or end < 0:
    raise SystemExit('v0.23 fixer: navigation block markers not found')
replacement = '''# Four navigation destinations; Settings hosts the notification master switch.
nav_lines = text.splitlines()
nav_found = False
for i, line in enumerate(nav_lines):
    if 'addNav(' in line and '"Search".equals(selected)' in line and 'this::showSearch' in line:
        indent = line[:len(line)-len(line.lstrip())]
        nav_lines[i] = line.replace('Search", "Search".equals(selected)', 'Caută", "Search".equals(selected)')
        bs = chr(92)
        nav_lines.insert(i+1, indent + 'addNav("⚙' + bs + 'nSetări", "Setări".equals(selected), this::showSettings);')
        nav_found = True
        break
if not nav_found:
    raise SystemExit('v0.23 patch failed: Search navigation line not found')
had_final_newline = text.endswith(chr(10))
text = chr(10).join(nav_lines) + (chr(10) if had_final_newline else '')

'''
s = s[:start] + replacement + s[end:]
p.write_text(s, encoding='utf-8')
print('prepare_v023 navigation matcher fixed')
