//package parser;
//
//import ast.Declarations;
//import exception.CompileException;
//import exception.FileException;
//import exception.SemanticException;
//import utils.ErrorHandler;
//
//import java.io.File;
//import java.util.*;
//
///**
// * @author ZP
// * @date 2023/4/5 1:01
// * @description TODO
// */
//public class LibraryLoader {
//    private List<String> loadPath;
//    private LinkedList<String> loadingLibraries;
//    private Map<String, Declarations> loadedLibraries;
//
//    public LibraryLoader() {
//        this(defaultLoadPath());
//    }
//
//    public LibraryLoader(List<String> loadPath) {
//        this.loadPath = new ArrayList<>(loadPath);
//        this.loadingLibraries = new LinkedList<>();
//        this.loadedLibraries = new HashMap<>();
//    }
//
//    public Declarations loadLibrary(String libid, ErrorHandler handler)
//            throws CompileException{
//        if (loadingLibraries.contains(libid)) {
//            throw new SemanticException("recursive import from "
//                                        + loadingLibraries.getLast()
//                                        + ": " + libid);
//        }
//
//        loadingLibraries.addLast(libid);
//
//        Declarations decls = loadedLibraries.get(libid);
//        if (decls != null) {
//            loadingLibraries.removeLast();
//            return decls;
//        }
//
//        decls = parser.Parser.parseDeclFile(searchLibrary(libid), this, handler);
//        loadedLibraries.put(libid, decls);
//        loadingLibraries.removeLast();
//        return decls;
//    }
//
//    public File searchLibrary(String libid) throws FileException {
//        for (String path : loadPath) {
//            File file = new File(path, libPath(libid) + ".hb");
//            if (file.exists()) {
//                return file;
//            }
//        }
//
//        throw new FileException(
//                "no such library header file: " + libid);
//    }
//
//    private String libPath(String id) {
//        return id.replace('.', File.separatorChar);
//    }
//
//    public static List<String> defaultLoadPath() {
//        List<String> pathes = new ArrayList<>();
//        pathes.add(".");
//        return pathes;
//    }
//}
