package ai.drfx.maximus.matrixai.studio.phonehost

import org.junit.Assert.*
import org.junit.Test

class PhoneHostPolicyTest {
    private val project = "12345678-abcd-abcd-abcd-abcdefabcdef"

    @Test fun exposesOnlyExplicitReadOnlyRoutes() {
        assertEquals(PhoneHostPolicy.Route.Capabilities,
            PhoneHostPolicy.route("GET", "/v1/capabilities"))
        assertEquals(PhoneHostPolicy.Route.Projects,
            PhoneHostPolicy.route("GET", "/v1/projects"))
        assertEquals(PhoneHostPolicy.Route.Manifest(project),
            PhoneHostPolicy.route("GET", "/v1/projects/$project/manifest"))
        assertEquals(PhoneHostPolicy.Route.Export(project),
            PhoneHostPolicy.route("GET", "/v1/projects/$project/export"))
    }

    @Test fun noExecutionUploadShellOrMutationOperations() {
        for (method in listOf("POST", "PUT", "DELETE", "PATCH", "HEAD")) {
            assertNull(PhoneHostPolicy.route(method, "/v1/projects"))
            assertNull(PhoneHostPolicy.route(method, "/v1/projects/$project/export"))
        }
        for (path in listOf("/v1/exec", "/v1/jobs", "/v1/models",
            "/v1/projects/$project/build", "/v1/terminal",
            "/v1/projects/$project/files/secret.txt")) {
            assertNull(PhoneHostPolicy.route("GET", path))
        }
    }

    @Test fun onlyExplicitBuildJobMethodsAreAccepted() {
        assertEquals(PhoneHostPolicy.Route.BuildRequest, PhoneHostPolicy.route("POST", "/v1/build-jobs"))
        assertEquals(PhoneHostPolicy.Route.Job(project, "cancel"), PhoneHostPolicy.route("POST", "/v1/build-jobs/$project/cancel"))
        assertEquals(PhoneHostPolicy.Route.Job(project, "artifact"), PhoneHostPolicy.route("GET", "/v1/build-jobs/$project/artifact"))
        for (suffix in listOf("approve", "start", "shell", "exec")) {
            assertNull(PhoneHostPolicy.route("POST", "/v1/build-jobs/$project/$suffix"))
        }
        assertNull(PhoneHostPolicy.route("GET", "/v1/build-jobs/$project/cancel"))
    }

    @Test fun rejectsPathConfusionAndEncodedTraversal() {
        for (path in listOf("/v1/projects/../../secret", "/v1/projects/$project/export?all=1",
            "/v1/projects/$project/%65xport", "/v1/projects/$project/..",
            "/v1/projects/$project/export#frag", "/v1/projects/$project/\\export",
            "/v1/projects/bad-id/export", "//v1/projects", "/v1/projects/")) {
            assertNull(path, PhoneHostPolicy.route("GET", path))
        }
    }
}
