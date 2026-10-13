import http.client
import json
import threading
import unittest

from worker import make_server, validate_configuration

TOKEN = "only-for-isolated-tests-32-plus-characters-0123456789"


class RemoteGatewayTests(unittest.TestCase):
    def setUp(self):
        self.server = make_server("127.0.0.1", 0, TOKEN)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=3)

    def request(self, method, path, token=None):
        connection = http.client.HTTPConnection("127.0.0.1", self.server.server_port, timeout=3)
        headers = {} if token is None else {"Authorization": "Bearer " + token}
        connection.request(method, path, headers=headers)
        res = connection.getresponse()
        data = json.loads(res.read().decode())
        status = res.status
        cache = res.getheader("Cache-Control")
        connection.close()
        return status, data, cache

    def test_no_token_cannot_discover(self):
        status, body, _ = self.request("GET", "/v1/capabilities")
        self.assertEqual(401, status)
        self.assertEqual("UNAUTHORIZED", body["error"])

    def test_wrong_token_is_rejected(self):
        status, _, _ = self.request("GET", "/v1/capabilities", TOKEN + "x")
        self.assertEqual(401, status)

    def test_authenticated_response_is_explicitly_discovery_only(self):
        status, body, cache = self.request("GET", "/v1/capabilities", TOKEN)
        self.assertEqual(200, status)
        self.assertEqual("maximus-runtime", body["protocol"])
        self.assertEqual(1, body["api_version"])
        self.assertFalse(body["execution_enabled"])
        self.assertEqual(["capabilities.read"], body["supported_operations"])
        self.assertEqual("no-store", cache)

    def test_commands_are_never_executed(self):
        for method in ("POST", "PUT", "DELETE"):
            status, body, _ = self.request(method, "/v1/jobs", TOKEN)
            self.assertEqual(405, status)
            self.assertEqual("EXECUTION_NOT_CONFIGURED", body["error"])

    def test_unsafe_bind_and_weak_tokens_are_rejected(self):
        for address in ("0.0.0.0", "192.168.1.1", "example.com", "::"):
            with self.assertRaises(ValueError):
                validate_configuration(address, TOKEN)
        with self.assertRaises(ValueError):
            validate_configuration("127.0.0.1", "short")


if __name__ == "__main__":
    unittest.main()
