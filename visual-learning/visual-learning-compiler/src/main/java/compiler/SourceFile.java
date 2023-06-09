package compiler;
import java.io.File;

public class SourceFile implements LdArg {
    static final String EXT_CFLAT_SOURCE = ".c";
    static final String EXT_ASSEMBLY_SOURCE = ".s";

    static final String[] KNOWN_EXTENSIONS = {
      EXT_CFLAT_SOURCE,
      EXT_ASSEMBLY_SOURCE,
    };

    private final String originalName;
    private String currentName;

    SourceFile(String name) {
        this.originalName = name;
        this.currentName = name;
    }

    public boolean isSourceFile() {
        return true;
    }

    public String toString() {
        return currentName;
    }

    public String path() {
        return currentName;
    }

    void setCurrentName(String name) {
        this.currentName = name;
    }

    boolean isKnownFileType() {
        String ext = extName(originalName);
        for (String e : KNOWN_EXTENSIONS) {
            if (e.equals(ext)) return true;
        }
        return false;
    }

    boolean isC0Source() {
        return extName(currentName).equals(EXT_CFLAT_SOURCE);
    }



    String asmFileName() {
        return replaceExt(EXT_ASSEMBLY_SOURCE);
    }





    private String replaceExt(String ext) {
        return baseName(originalName, true) + ext;
    }

    private String baseName(String path) {
        return new File(path).getName();
    }

    private String baseName(String path, boolean deExtension) {
        if (deExtension) {
            // split filename.c -> filename
            return new File(path).getName().replaceFirst("\\.[^.]*$", "");
        }
        else {
            return baseName(path);
        }
    }

    private String extName(String path) {
        int idx = path.lastIndexOf(".");
        if (idx < 0) return "";
        return path.substring(idx);
    }
}
