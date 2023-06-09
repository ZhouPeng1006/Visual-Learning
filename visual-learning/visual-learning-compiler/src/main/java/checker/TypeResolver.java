package checker;

import ast.*;
import ast.Visitor;
import entity.*;
import type.*;
import utils.ErrorHandler;

public class TypeResolver extends Visitor
        implements EntityVisitor<Void>, DeclarationVisitor<Void> {
    private final TypeTable typeTable;
    private final ErrorHandler errorHandler;

    public TypeResolver(TypeTable typeTable, ErrorHandler errorHandler) {
        this.typeTable = typeTable;
        this.errorHandler = errorHandler;
    }

    public void resolve(AST ast) {
        for (Entity e : ast.definitions()) {
            e.accept(this);
        }
    }

    private void bindType(TypeNode n) {
        if (n.isResolved()) return;
        n.setType(typeTable.get(n.typeRef()));
    }

    //
    // Declarations
    //
    public Void visit(TypedefNode typedef) {
        bindType(typedef.typeNode());
        bindType(typedef.realTypeNode());
        return null;
    }

    //
    // Entities
    //

    public Void visit(DefinedVariable var) {
        bindType(var.typeNode());
        if (var.hasInitializer()) {
            visitExpr(var.initializer());
        }
        return null;
    }


    public Void visit(Constant c) {
        bindType(c.typeNode());
        visitExpr(c.value());
        return null;
    }

    public Void visit(DefinedFunction func) {
        resolveFunctionHeader(func);
        visitStmt(func.body());
        return null;
    }

    private void resolveFunctionHeader(Function func) {
        // 把DefinedFunction下的typeNode中的typeRef（FunctionTypeRef）和type绑定（FunctionType）
        bindType(func.typeNode());
        for (Parameter param : func.parameters()) {
            // arrays must be converted to pointers in a function parameter.
            Type t = typeTable.getParamType(param.typeNode().typeRef());
            param.typeNode().setType(t);
        }
    }

    //
    // Expressions
    //

    public Void visit(BlockNode node) {
        for (DefinedVariable var : node.variables()) {
            var.accept(this);
        }
        visitStmts(node.stmts());
        return null;
    }

    public Void visit(CastNode node) {
        bindType(node.typeNode());
        super.visit(node);
        return null;
    }

    public Void visit(SizeofExprNode node) {
        bindType(node.typeNode());
        super.visit(node);
        return null;
    }

    public Void visit(SizeofTypeNode node) {
        bindType(node.operandTypeNode());
        bindType(node.typeNode());
        super.visit(node);
        return null;
    }

    public Void visit(IntegerLiteralNode node) {
        bindType(node.typeNode());
        return null;
    }

    public Void visit(StringLiteralNode node) {
        bindType(node.typeNode());
        return null;
    }

    private void error(Node node, String msg) {
        errorHandler.error(node.location(), msg);
    }
}
