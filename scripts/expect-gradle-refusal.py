#!/usr/bin/env python3
"""Confirm an intentional guard failure, without hiding unrelated Gradle errors."""
import argparse
from pathlib import Path
import subprocess
import sys

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--message', required=True)
parser.add_argument('--label', required=True, choices=['source-only', 'legacy-signing', 'parallel-identity'])
parser.add_argument('command', nargs=argparse.REMAINDER)
args = parser.parse_args()
command = args.command[1:] if args.command[:1] == ['--'] else args.command
result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
text = result.stdout.decode(errors='replace')
folder = Path('artifacts/diagnostics'); folder.mkdir(parents=True, exist_ok=True)
(folder / (args.label + '-refusal.log')).write_text(text)
if result.returncode != 0 and args.message in text:
    print('Expected refusal confirmed: ' + args.label)
    sys.exit(0)
message = ('Guard did not refuse packaging.' if result.returncode == 0 else 'Unexpected Gradle failure, not the expected guard.') + '\n' + text[-14000:]
print('::error title=Gradle refusal verification::' + message.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A'))
sys.exit(1)
