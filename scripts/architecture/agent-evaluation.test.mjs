// The existing Architecture CI test entry also runs the Agent evaluation transport/offline contracts.
// No model or external service is called by these tests.
import '../ai/run-evaluation.test.mjs';
import '../ai/scenario-evaluation.test.mjs';
import '../ai/read-j2-evidence.test.mjs';

// F-039 5/5 golden acceptance is structural-only here; NEVER fake live pilot PASS.
import '../ai/f039-golden-acceptance.test.mjs';
