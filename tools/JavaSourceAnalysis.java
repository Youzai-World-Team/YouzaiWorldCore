import com.sun.source.util.JavacTask;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** 使用已解析依赖执行 Java 类型和符号分析，不生成类文件或运行注解处理器。 */
public final class JavaSourceAnalysis {
    public static void main(String[] args) throws Exception {
        Path project = Path.of(args[0]);
        String classpath = String.join(java.io.File.pathSeparator, Files.readAllLines(Path.of(args[1])));
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
             var paths = Files.walk(project.resolve("src"))) {
            var sources = paths.filter(p -> p.toString().endsWith(".java")).toList();
            var options = List.of("--release", "25", "-proc:none", "-Xmaxerrs", "1000", "-classpath", classpath);
            var task = (JavacTask) compiler.getTask(null, manager, diagnostics, options, null,
                    manager.getJavaFileObjectsFromPaths(sources));
            for (var tree : task.parse()) { }
            for (var element : task.analyze()) { }
            long errors = 0;
            for (var diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() != Diagnostic.Kind.ERROR) continue;
                errors++;
                System.out.println(diagnostic.getSource().getName() + ":" + diagnostic.getLineNumber()
                        + ": " + diagnostic.getMessage(java.util.Locale.ROOT));
            }
            System.out.println("Java 类型与符号分析：" + sources.size() + " 个文件，" + errors + " 个错误。");
            if (errors > 0) System.exit(1);
        }
    }
}
