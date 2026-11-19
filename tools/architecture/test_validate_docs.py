"""Guards for the architecture documentation validator."""
import unittest

from validate_docs import mermaid_text_hazards, validate


class MermaidTextHazardTest(unittest.TestCase):
    def test_semicolon_in_sequence_message_is_reported(self):
        block = "sequenceDiagram\n    A->>B: Validate input; allocate UUID\n"
        self.assertEqual(len(mermaid_text_hazards(block)), 1)

    def test_hash_in_state_label_is_reported(self):
        block = "stateDiagram-v2\n    A --> B: retry #2\n"
        self.assertEqual(len(mermaid_text_hazards(block)), 1)

    def test_comma_separated_text_and_comments_are_accepted(self):
        block = "sequenceDiagram\n    %% comment; ignored\n    A->>B: Validate input, allocate UUID\n"
        self.assertEqual(mermaid_text_hazards(block), [])

    def test_repository_architecture_graph_is_valid(self):
        self.assertEqual(validate(), 0)


if __name__ == "__main__":
    unittest.main()
