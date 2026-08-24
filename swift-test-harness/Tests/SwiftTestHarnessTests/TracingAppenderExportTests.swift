import Testing
import TracingAppender

@Suite("TracingAppender Swift Export Tests")
struct TracingAppenderExportTests {
    @Test("TracingAppender constants and enums work from Swift")
    func swiftModuleLoads() {
        #expect(DEFAULT_BUFFERED_LINES_LIMIT == 128_000)
        #expect(rolling.RotationKind.Hourly.description == "Hourly")
    }
}
