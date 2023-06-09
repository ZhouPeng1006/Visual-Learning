package compiler;

import exception.*;
import type.TypeTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;

public class Options {
    private String outputFileName;

    private List<LdArg> ldArgs;
    private List<SourceFile> sourceFiles;

    public static Options parse(String[] args) {
        Options opts = new Options();
        opts.parseArgs(args);
        return opts;
    }

    public CompilerMode mode() {
        return mode;
    }

    public List<SourceFile> sourceFiles() {
        return sourceFiles;
    }

    String asmFileNameOf(SourceFile src) {
        if (outputFileName != null && mode == CompilerMode.Compile) {
            return outputFileName;
        }
        return src.asmFileName();
    }

    public TypeTable typeTable() {
        return TypeTable.ilp32();
    }


    void parseArgs(String[] origArgs) {
        sourceFiles = new ArrayList<>();
        ldArgs = new ArrayList<>();
        ListIterator<String> args = Arrays.asList(origArgs).listIterator();
        while (args.hasNext()) {
            String arg = args.next();
            if (arg.startsWith("-")) {
                if (CompilerMode.isModeOption(arg)) {
                    if (mode != null) {
                        parseError(mode.toOption() + " option and "
                                   + arg + " option is exclusive");
                    }
                    mode = CompilerMode.fromOption(arg);
                }
                else if (arg.startsWith("-o")) {
                    outputFileName = getOptArg(arg, args);
                }
                else {
                    parseError("unknown option: " + arg);
                }
            }
            else {
                ldArgs.add(new SourceFile(arg));
            }
        }
        // args has more arguments when "--" is appeared.
        while (args.hasNext()) {
            ldArgs.add(new SourceFile(args.next()));
        }

        if (mode == null) {
            mode = CompilerMode.Assemble;
        }
        sourceFiles = selectSourceFiles(ldArgs);
        if (sourceFiles.isEmpty()) {
            parseError("no input file");
        }
        for (SourceFile src : sourceFiles) {
            if (! src.isKnownFileType()) {
                parseError("unknown file type: " + src.path());
            }
        }
    }

    private void parseError(String msg) {
        throw new OptionParseError(msg);
    }

    private List<SourceFile> selectSourceFiles(List<LdArg> args) {
        List<SourceFile> result = new ArrayList<>();
        for (LdArg arg : args) {
            if (arg.isSourceFile()) {
                result.add((SourceFile)arg);
            }
        }
        return result;
    }

    private String getOptArg(String opt, ListIterator<String> args) {
        String path = opt.substring(2);
        if (path.length() != 0) {       // -Ipath
            return path;
        }
        else {                          // -I path
            return nextArg(opt, args);
        }
    }

    private String nextArg(String opt, ListIterator<String> args) {
        if (! args.hasNext()) {
            parseError("missing argument for " + opt);
        }
        return args.next();
    }

    private CompilerMode mode;
}
