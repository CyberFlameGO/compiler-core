package net.fluidlang.compiler;

import lombok.Getter;
import lombok.SneakyThrows;
import net.fluidlang.compiler.ast.*;
import net.fluidlang.compiler.err.LiquidErrorHandler;
import net.fluidlang.compiler.err.SemanticAnalyzer;
import net.fluidlang.compiler.util.CompilerLogger;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.fusesource.jansi.AnsiConsole;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.Callable;

import static picocli.CommandLine.*;

@Command(
		name = "liqc",
		description = "LiquidLang Compiler",
		version = "1.0.0"
)
public class Main implements Callable<Integer> {

	private Main() {}

	@Parameters
	private static Set<Path> targets;

	@Getter
	@Option(names = {"--ansi-color", "-c"})
	private static boolean ansi;

	@Getter
	@Option(names = {"--verbose", "-V"})
	private static boolean verbose;

	public static void main(String[] args) {
		System.exit(new CommandLine(new Main()).execute(args));
	}

	@Override
	@SneakyThrows
	public Integer call() {
		if(targets == null) {
			System.err.println("liqc: no input files");
			System.err.println("compilation terminated.");
			return 1;
		}
		boolean hasOrigin = false;
		boolean compilingLib = false;
		Path mainTarget = null;
		for(Path p : targets) {
			if(!p.getFileName().toString().endsWith(".lq")) {
				System.err.println("liqc: files must end with the .lq extension");
				System.err.println("compilation terminated.");
				return 1;
			}
			if(p.getFileName().toString().equals("main.lq") || p.getFileName().toString().equals("lib.lq")) {
				if(!compilingLib && p.getFileName().toString().equals("lib.lq")) compilingLib = true;
				if(hasOrigin) {
					System.err.println("liqc: main.lq and lib.lq exist; there can only be one of them");
					System.err.println("compilation terminated.");
					return 1;
				}
				hasOrigin = true;
				mainTarget = p;
			}
		}
		if(!hasOrigin) {
			System.err.println("liqc: no main.lq or lib.lq present");
			System.err.println("compilation terminated.");
			return 1;
		}

		CompilerLogger.info("compiling library from " + targets.size() + " modules");

		parse(mainTarget);
		if(ansi) AnsiConsole.systemUninstall();
		if(LiquidErrorHandler.errors >= 1) CompilerLogger.terminate(LiquidErrorHandler.errors + " errors found");
		return 0;
	}

	@SneakyThrows
	@VisibleForTesting
	public static void parse(@NotNull Path origin) {
		var lex = new FLexer(CharStreams.fromPath(origin));
		lex.removeErrorListeners();
		lex.addErrorListener(LiquidErrorHandler.INSTANCE);
		var parser = new FParser(new CommonTokenStream(lex));
		parser.removeErrorListeners();
		parser.addErrorListener(LiquidErrorHandler.INSTANCE);
		new ParseTreeWalker().walk(new SemanticAnalyzer(), parser.compilationUnit());
	}

	private static final class TestWalker extends FParserBaseListener {

		@Override
		public void enterFunction(FParser.FunctionContext ctx) {
			System.out.println("(FUNC) Entered function '" + ctx.IDENTIFIER() + "' with return type '" + (ctx.type() != null ? ctx.type().getText() : "void") + "'");
		}

		@Override
		public void enterFormalParameter(FParser.FormalParameterContext ctx) {
			System.out.println("(FUNC) Entered formal parameter " + ctx.getText());
		}

		@Override
		public void enterImportStatement(FParser.ImportStatementContext ctx) {
			System.out.println("(BASE) Entered import statement to import " + ctx.IDENTIFIER().getText());
		}

		@Override
		public void enterValue(FParser.ValueContext ctx) {
			System.out.println("Value entered: " + ctx.getText());
		}
	}

}
