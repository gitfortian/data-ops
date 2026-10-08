// The existing Architecture CI test entry also runs the Agent evaluation transport/offline contracts.
// No model or external service is called by these tests.
import '../ai/run-evaluation.test.mjs';
import '../ai/scenario-evaluation.test.mjs';
import '../ai/read-j2-evidence.test.mjs';
