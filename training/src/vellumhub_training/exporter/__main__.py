import argparse
import sys
from .config import DatabaseConfig
from .export import export_dataset


def main():
    parser = argparse.ArgumentParser(description="Export canonical facts and replicated reading history")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    try:
        result = export_dataset(DatabaseConfig.from_env("CATALOG"),
                                DatabaseConfig.from_env("ENGAGEMENT"), args.output)
    except Exception as error:
        # Driver/configuration exceptions can embed DSNs and passwords.
        print(f"Export failed ({type(error).__name__}). Check source access, schema, references and output path.",
              file=sys.stderr)
        return 1
    print(f"Exported {result['artifacts']['books']['rows']} books and "
          f"{result['artifacts']['userBookSignals']['rows']} signals")
    return 0


if __name__ == "__main__":
    sys.exit(main())
