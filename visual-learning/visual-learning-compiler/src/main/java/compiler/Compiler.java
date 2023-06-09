package compiler;

import ast.AST;
import checker.DereferenceChecker;
import checker.LocalResolver;
import checker.TypeChecker;
import checker.TypeResolver;
import exception.*;
import org.bytedeco.llvm.LLVM.LLVMModuleRef;
import parser.Parser;
import type.TypeTable;
import utils.ErrorHandler;
import java.io.*;
import java.util.List;

import static org.bytedeco.llvm.global.LLVM.LLVMDisposeModule;

public class Compiler {

    static final public String ProgramName = "scc";
//
//    static public void main(String[] args) {
//        String[] argss = {"test3.c"};
//        new Compiler(ProgramName).commandMain(argss);
//    }


    private final ErrorHandler errorHandler;

    public Compiler(String programName) {
        this.errorHandler = new ErrorHandler(programName);
    }

    public void commandMain(String[] args) {
        Options opts = parseOptions(args);
        try {
            List<SourceFile> srcs = opts.sourceFiles();
            build(srcs, opts);
            System.exit(0);
        }
        catch (CompileException ex) {
            errorHandler.error(ex.getMessage());
            System.exit(1);
        }
    }

    private Options parseOptions(String[] args) {
        try {
            return Options.parse(args);
        }
        catch (OptionParseError err) {
            errorHandler.error(err.getMessage());
            System.exit(1);
            return null;
        }
    }

    public void build(List<SourceFile> srcs, Options opts)
                                        throws CompileException {
        for (SourceFile src : srcs) {
            if (src.isC0Source()) {
                // generate output filename
                String destPath = opts.asmFileNameOf(src);
                compile(src.path(), destPath, opts);
                src.setCurrentName(destPath);
            }
        }
    }

    public void compile(String srcPath, String destPath,
                        Options opts) throws CompileException {
        AST ast = parseFile(srcPath);
        TypeTable types = opts.typeTable();
        AST sem = semanticAnalyze(ast, types, opts);

        LLVMModuleRef module = irGenerate(sem);
        asmGenerate(module, destPath);
        LLVMDisposeModule(module);
    }

    public AST parseFile(String path) throws FileException, SyntaxException {
        return Parser.parseFile(new File(path), errorHandler);
    }

    public AST semanticAnalyze(AST ast, TypeTable types,
                Options opts) throws SemanticException {
        new LocalResolver(errorHandler).resolve(ast);
        new TypeResolver(types, errorHandler).resolve(ast);

        if (opts.mode() == CompilerMode.DumpReference) {
            ast.dump();
            return ast;
        }
        new DereferenceChecker(types, errorHandler).check(ast);
        new TypeChecker(types, errorHandler).check(ast);
        return ast;
    }

    public LLVMModuleRef irGenerate(AST ast) throws SemanticException {
        return  new LLVMIRGenerator().llvmGenerate(ast);
    }

    public void asmGenerate(LLVMModuleRef  module, String destPath){
        new CodeGenerator().asmGenerate(module, destPath);
    }

}
