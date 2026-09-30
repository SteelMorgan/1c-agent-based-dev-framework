"""Unit-слой review-swarm: доступ к scripts/ роя и к scripts/ harness без установки пакета."""
import sys
from pathlib import Path

SKILLS_DIR = Path(__file__).resolve().parents[3]
for scripts_dir in (
    SKILLS_DIR / "review-swarm" / "scripts",
    SKILLS_DIR / "review-harness" / "scripts",
):
    if str(scripts_dir) not in sys.path:
        sys.path.insert(0, str(scripts_dir))
