package ast;

import entity.*;
import java.util.ArrayList;
import java.util.List;

public class AST extends Node {

    public Declarations declarations;
    public ToplevelScope scope;
    public ConstantTable constantTable;

    public AST(Location source, Declarations declarations) {
        super();
        this.source = source;
        this.declarations = declarations;
    }

    public Location source;

    public Location location() {
        return source;
    }



    public List<Entity> definitions() {
        List<Entity> result = new ArrayList<>();
        result.addAll(declarations.defvars);
        result.addAll(declarations.defuns);
        result.addAll(declarations.constants);
        return result;
    }

    public List<Constant> constants() {
        return declarations.constants();
    }

    public List<DefinedVariable> definedVariables() {
        return declarations.defvars();
    }

    public List<DefinedFunction> definedFunctions() {
        return declarations.defuns();
    }

    // called by LocalResolver
    public void setScope(ToplevelScope scope) {
        if (this.scope != null) {
            throw new Error("must not happen: ToplevelScope set twice");
        }
        this.scope = scope;
    }

    public ToplevelScope scope() {
        if (this.scope == null) {
            throw new Error("must not happen: AST.scope is null");
        }
        return scope;
    }

    // called by LocalResolver
    public void setConstantTable(ConstantTable table) {
        if (this.constantTable != null) {
            throw new Error("must not happen: ConstantTable set twice");
        }
        this.constantTable = table;
    }



    protected void _dump(Dumper d) {
        d.printNodeList("variables", definedVariables());
        d.printNodeList("functions", definedFunctions());

    }


    public StmtNode getSingleMainStmt() {
        for (DefinedFunction f : definedFunctions()) {
            if (f.name().equals("main")) {
                if (f.body().stmts().isEmpty()) {
                    return null;
                }
                return f.body().stmts().get(0);
            }
        }
        return null;
    }

}
