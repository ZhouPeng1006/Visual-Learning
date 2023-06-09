package ast;

import entity.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class Declarations {
    Set<DefinedVariable> defvars = new LinkedHashSet<>();
    Set<DefinedFunction> defuns = new LinkedHashSet<>();
    Set<Constant> constants = new LinkedHashSet<>();


    public void add(Declarations decls) {
        defvars.addAll(decls.defvars);
        defuns.addAll(decls.defuns);
        constants.addAll(decls.constants);

    }


    public void addDefvars(List<DefinedVariable> vars) {
        defvars.addAll(vars);
    }

    public List<DefinedVariable> defvars() {
        return new ArrayList<>(defvars);
    }

    public void addConstant(Constant c) {
        constants.add(c);
    }

    public List<Constant> constants() {
        return new ArrayList<>(constants);
    }

    public void addDefun(DefinedFunction func) {
        defuns.add(func);
    }

    public List<DefinedFunction> defuns() {
        return new ArrayList<>(defuns);
    }



}
