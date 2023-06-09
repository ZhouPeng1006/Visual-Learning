package compiler;

import ast.*;
import entity.*;
import exception.*;
import ast.Op;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.*;
import type.*;
import java.util.List;

import static org.bytedeco.llvm.global.LLVM.*;


public class LLVMIRGenerator {
    public LLVMIRGenerator() {
        this.context = LLVMContextCreate();;
        this.builder = LLVMCreateBuilderInContext(context);;
    }

    LLVMContextRef context;
    LLVMModuleRef module;
    LLVMBuilderRef builder;
    LLVMValueRef currFunc;

    public LLVMModuleRef llvmGenerate(AST ast) throws SemanticException {
        this.module = LLVMModuleCreateWithName(ast.source.sourceName());

        // Transform topLevelScope variables
        for (DefinedVariable var : ast.definedVariables()) {
            definedVariablesGenerate(module, builder, var, ast.scope());
        }

        // Declare global constants
        for (Constant constant : ast.constants()) {
            LLVMTypeRef constType = typeToLLVMType(constant.type());
            LLVMValueRef llvmConst = LLVMAddGlobal(module, constType, constant.name());

//            LLVMSetLinkage(llvmConst, LLVMInternalLinkage);
            LLVMValueRef llvmInitVal = constToLLVMValue(builder, constant.value());
            LLVMSetInitializer(llvmConst, llvmInitVal);
        }
        for (DefinedFunction func : ast.definedFunctions()) {
            LLVMTypeRef[] paramTypes = typesToLLVMTypes(func.parameters(), func.lvarScope());
            LLVMTypeRef funcType = LLVMFunctionType(typeToLLVMType(func.returnType()), new PointerPointer<>(paramTypes),
                    paramTypes.length,
                    0
            );
            LLVMAddFunction(module, func.name(), funcType);
        }
        for (DefinedFunction func : ast.definedFunctions()) {
            LLVMValueRef llvmFunc = LLVMGetNamedFunction(module, func.name());
            this.currFunc = llvmFunc;
            compileParams(func, llvmFunc);

            LLVMBasicBlockRef entry = LLVMAppendBasicBlock(llvmFunc, func.name() + ".entry");
            LLVMPositionBuilderAtEnd(builder, entry);

            // Generate function body
            stmtToLLVM(module, builder, func.body(), func.body().scope());
            LLVMPositionBuilderAtEnd(builder, LLVMGetEntryBasicBlock(currFunc));
        }

        LLVMDumpModule(module);
        LLVMDisposeBuilder(builder);
        return module;
    }

    public void compileParams(DefinedFunction func, LLVMValueRef llvmFunc){
        int i = 0;
        for (DefinedVariable param : func.parameters()) {
            LLVMValueRef llvmParam = LLVMGetParam(llvmFunc, i);
            LLVMSetValueName(llvmParam, param.name());
            i++;
        }
    }


    private LLVMValueRef stmtToLLVM(LLVMModuleRef module, LLVMBuilderRef builder, StmtNode node, Scope scope) throws SemanticException {
        if (node instanceof ExprStmtNode) {
            return exprToLLVM(module, builder, ((ExprStmtNode) node).expr(), scope);
        }
        else if (node instanceof ReturnNode) {
            ExprNode expr = ((ReturnNode) node).expr();
            LLVMValueRef ret = exprToLLVM(module, builder, expr, scope);
            return LLVMBuildRet(builder, ret);
        }
        else if (node instanceof BlockNode) {
            BlockNode body = ((BlockNode) node);
            List<StmtNode> stmts = body.stmts();
            List<DefinedVariable> variables = body.variables();

            for (DefinedVariable var : variables) {
                definedVariablesGenerate(module, builder, var, body.scope());
            }
            for (StmtNode stmt : stmts) {
                stmtToLLVM(module, builder, stmt, body.scope());
            }
        }
        else if (node instanceof IfNode) {
            LLVMBasicBlockRef thenBlock = LLVMAppendBasicBlockInContext(context, currFunc, "if.then");
            LLVMBasicBlockRef elseBlock = LLVMAppendBasicBlockInContext(context, currFunc, "if.else");
            LLVMBasicBlockRef endBlock = LLVMAppendBasicBlockInContext(context, currFunc, "if.end");
            ExprNode cond = ((IfNode) node).cond();
            BlockNode thenBody = (BlockNode) ((IfNode) node).thenBody();
            BlockNode elseBody = (BlockNode) ((IfNode) node).elseBody();
            // Conditional branch
            LLVMValueRef condRes = buildICmp(module, builder, cond, scope);
            if (elseBody != null) {
                LLVMBuildCondBr(builder, condRes, thenBlock, elseBlock);
            } else {
                LLVMBuildCondBr(builder, condRes, thenBlock, endBlock);
            }
            LLVMPositionBuilderAtEnd(builder, thenBlock);
            stmtToLLVM(module, builder, thenBody, thenBody.scope());
            LLVMBuildBr(builder, endBlock);

            if (elseBody != null) {
                LLVMPositionBuilderAtEnd(builder, elseBlock);
                stmtToLLVM(module, builder, elseBody, elseBody.scope());
                LLVMBuildBr(builder, endBlock);
            }
            LLVMPositionBuilderAtEnd(builder, endBlock);

        }
        else if (node instanceof ForNode) {
            LLVMBasicBlockRef loop = LLVMAppendBasicBlockInContext(context, currFunc, "loop");
            LLVMBasicBlockRef loopBody = LLVMAppendBasicBlockInContext(context, currFunc, "loop.body");
            LLVMBasicBlockRef loopEnd = LLVMAppendBasicBlockInContext(context, currFunc, "loop.end");
            ExprNode init = ((ExprStmtNode) ((ForNode) node).init()).expr();
            ExprNode cond = ((ForNode) node).cond();
            // Set init
            setAlloca(module, builder, init, scope);
            LLVMBuildBr(builder, loop);
            LLVMPositionBuilderAtEnd(builder, loop);
            LLVMValueRef cmp = buildICmp(module, builder, cond, scope);
            LLVMBuildCondBr(builder, cmp, loopBody, loopEnd);
            LLVMPositionBuilderAtEnd(builder, loopBody);
            stmtToLLVM(module, builder, ((ForNode) node).body(), ((BlockNode) ((ForNode) node).body()).scope());
            // incr
            stmtToLLVM(module, builder, ((ForNode) node).incr(), ((BlockNode) ((ForNode) node).body()).scope());
            LLVMBuildBr(builder, loop);
            LLVMPositionBuilderAtEnd(builder, loopEnd);
        }
        else if (node instanceof WhileNode) {
            LLVMBasicBlockRef whileCondBlock = LLVMAppendBasicBlockInContext(context, currFunc, "while.cond");
            LLVMBasicBlockRef whileLoopBlock = LLVMAppendBasicBlockInContext(context, currFunc, "while.loop");
            LLVMBasicBlockRef endBlock = LLVMAppendBasicBlockInContext(context, currFunc, "while.end");
            ExprNode cond = ((WhileNode) node).cond();
            BlockNode body = (BlockNode) ((WhileNode) node).body();
            LLVMBuildBr(builder, whileCondBlock);

            LLVMPositionBuilderAtEnd(builder, whileCondBlock);
            LLVMValueRef cmp = buildICmp(module, builder, cond, scope);
            LLVMBuildCondBr(builder, cmp, whileLoopBlock, endBlock);

            LLVMPositionBuilderAtEnd(builder, whileLoopBlock);
            stmtToLLVM(module, builder, body, body.scope());
            LLVMBuildBr(builder, whileCondBlock);

            LLVMPositionBuilderAtEnd(builder, endBlock);
        }
        else if (node instanceof DoWhileNode){
            BlockNode body = (BlockNode)((DoWhileNode) node).body();
            ExprNode cond = ((DoWhileNode) node).cond();
            LLVMBasicBlockRef doWhileCond = LLVMAppendBasicBlockInContext(context, currFunc, "doWhile.cond");
            LLVMBasicBlockRef doWhileBody = LLVMAppendBasicBlockInContext(context, currFunc, "doWhile.body");
            LLVMBasicBlockRef doWhileEnd = LLVMAppendBasicBlockInContext(context, currFunc, "doWhile.end");

            LLVMBuildBr(builder,doWhileBody);
            LLVMPositionBuilderAtEnd(builder, doWhileBody);

            stmtToLLVM(module, builder, body, body.scope());

            LLVMBuildBr(builder, doWhileCond);
            LLVMPositionBuilderAtEnd(builder, doWhileCond);
            LLVMValueRef cmp = buildICmp(module, builder, cond, scope);
            LLVMBuildCondBr(builder,cmp, doWhileBody,doWhileEnd);

            LLVMPositionBuilderAtEnd(builder, doWhileEnd);

        }
        else if (node instanceof BreakNode) { }
        else if (node instanceof CaseNode) { }
        else if (node instanceof ContinueNode) { }
        else if (node instanceof LabelNode){
            String name = ((LabelNode) node).name();
            LLVMBasicBlockRef labelBlock = LLVMAppendBasicBlockInContext(context, currFunc, name);
            StmtNode body = ((LabelNode) node).stmt();

        }
        return null;
    }

    private LLVMValueRef buildICmp(LLVMModuleRef module, LLVMBuilderRef builder, ExprNode expr, Scope scope) {
        if (expr instanceof LogicalAndNode) {
            LLVMValueRef cmpR = buildICmp(module, builder, ((BinaryOpNode) expr).right(), scope);
            LLVMValueRef cmpL = buildICmp(module, builder, ((BinaryOpNode) expr).left(), scope);
            return LLVMBuildAnd(builder, cmpL, cmpR, "and");

        } else if (expr instanceof LogicalOrNode) {
            LLVMValueRef cmpR = buildICmp(module, builder, ((BinaryOpNode) expr).right(), scope);
            LLVMValueRef cmpL = buildICmp(module, builder, ((BinaryOpNode) expr).left(), scope);
            return LLVMBuildOr(builder, cmpL, cmpR, "or");
        } else {
            ExprNode right = ((BinaryOpNode) expr).right();
            long cmpVal = ((IntegerLiteralNode) right).value();

            VariableNode left = (VariableNode) ((BinaryOpNode) expr).left();
            String op = ((BinaryOpNode) expr).operator();
            int iCmpOp = getICmpOp(op);
            LLVMValueRef lhs = LLVMBuildLoad(builder, scope.getAlloca(left.name()), left.name());
            return LLVMBuildICmp(builder, iCmpOp, lhs, LLVMConstInt(selectSize(right.type()), cmpVal, signed(((BinaryOpNode) expr).right())), "cmp");

        }
    }

    public int signed(ExprNode exprNode){
        return  exprNode.type().isSigned() ? 0 :1;
    }

    private LLVMValueRef exprToLLVM(LLVMModuleRef module, LLVMBuilderRef builder, ExprNode expr, Scope scope) {
        if (expr instanceof BinaryOpNode) {
            BinaryOpNode node = ((BinaryOpNode) expr);

            String operator = ((BinaryOpNode) expr).operator();
            LLVMValueRef right = exprToLLVM(module, builder, node.right(), scope);
            LLVMValueRef left = exprToLLVM(module, builder, node.left(), scope);

            if (((BinaryOpNode) expr).left().isConstant() && ((BinaryOpNode) expr).right().isConstant()) {
                IntegerLiteralNode l = (IntegerLiteralNode) ((BinaryOpNode) expr).left();
                IntegerLiteralNode r = (IntegerLiteralNode) ((BinaryOpNode) expr).right();
                long value = constantFold(l, operator, r);
                return LLVMConstInt(selectSize(l.type()), value, signed(l));
            } else {
                return compileBinaryOp(module, builder, right, left, node.operator(), scope);
            }
        }
        else if (expr instanceof VariableNode) {
            Entity e = ((VariableNode) expr).entity();
            if (e.isConstant()) {
                ExprNode value = e.value();
                if (value instanceof IntegerLiteralNode) {
                    long value1 = ((IntegerLiteralNode) value).value();
                    LLVMValueRef yPtr = LLVMBuildAlloca(builder, selectSize(e.type()), e.name());
                    return LLVMBuildStore(builder,
                            LLVMConstInt(selectSize(value.type()), value1, signed(value)),
                            yPtr);
                }
            } else if (e.isDefined()) {
                if (e.isParameter()){
                    return LLVMGetParam(currFunc, getParamIndex(e.name(), scope));
//                    return LLVMBuildLoad(builder, var, e.name());
                } else {
                    LLVMValueRef var = scope.getAlloca(e.name());
                    return LLVMBuildLoad(builder, var, e.name());
                }

            }
        }
        else if (expr instanceof IntegerLiteralNode) {
            // Literal value
            long value = ((IntegerLiteralNode) expr).value();
            LLVMTypeRef valueType = typeToLLVMType(expr.type());
            return LLVMConstInt(valueType, value, signed(expr));
        }
        else if (expr instanceof FuncallNode) {
            DefinedFunction func = (DefinedFunction) ((VariableNode) ((FuncallNode) expr).expr()).entity();
            List<ExprNode> args = ((FuncallNode) expr).args();

            LLVMValueRef function = LLVMGetNamedFunction(module, func.name());
            LLVMTypeRef functionTypeRef = LLVMGetElementType(LLVMTypeOf(function));
            LLVMValueRef[] params = argsToLLVMValue(module, builder, args, scope);
            PointerPointer<LLVMValueRef> llvmValueRefPointerPointer = new PointerPointer<>(params);
            return LLVMBuildCall2(builder, functionTypeRef, function, llvmValueRefPointerPointer, params.length, func.name());


        }
        else if (expr instanceof AssignNode) {
            AssignNode node = (AssignNode) expr;
            LLVMValueRef rhs = exprToLLVM(module, builder, node.rhs(), scope);
            LLVMValueRef orig = scope.getAlloca(((VariableNode) node.lhs()).name());
            return LLVMBuildStore(builder, rhs, orig);
        }
        else if (expr instanceof OpAssignNode) {
            OpAssignNode node = (OpAssignNode) expr;
            LLVMValueRef rhs = exprToLLVM(module, builder, node.rhs(), scope);
            LLVMValueRef lhs = exprToLLVM(module, builder, node.lhs(), scope);
            // cont(lhs += rhs) -> lhs = lhs + rhs; cont(lhs)
            LLVMValueRef res = compileBinaryOp(module, builder, rhs, lhs, node.operator(), scope);
            LLVMValueRef orig = scope.getAlloca(((VariableNode) node.lhs()).name());
            return LLVMBuildStore(builder, res, orig);

        }
        else if (expr instanceof SuffixOpNode) {
            ExprNode node = ((SuffixOpNode) expr).expr();
            LLVMValueRef i = scope.getAlloca(((VariableNode) node).name());

            LLVMValueRef load = LLVMBuildLoad(builder, i, ((VariableNode) node).name());
            LLVMValueRef add = LLVMBuildAdd(builder, load, LLVMConstInt(selectSize(node.type()), 1, signed(node)), "add");
            LLVMBuildStore(builder, add, i);
        }
        else if (expr instanceof UnaryOpNode) {
            if ("+".equals(((UnaryOpNode) expr).operator())) {
                constToLLVMValue(builder, expr);
            } else {
                return transformUnary(module, builder, expr, scope);
            }
        }
        else if (expr instanceof AddressNode) {
            String name = ((VariableNode) ((AddressNode) expr).expr()).name();
            LLVMValueRef var = scope.getAlloca(name);
            return LLVMBuildBitCast(builder, var, LLVMPointerType(selectSize(expr.type()), 0), name + ".ptr");
        }
        return null;
    }

    private int getParamIndex(String name, Scope scope) {
        return scope.getScopeParam(name);
    }

    private LLVMValueRef transformUnary(LLVMModuleRef module, LLVMBuilderRef builder, ExprNode expr, Scope scope) {
        UnaryOpNode node = (UnaryOpNode) expr;
        LLVMValueRef val = exprToLLVM(module, builder, node.expr(), scope);
        switch (node.operator()) {
            case "-":
                return LLVMBuildNeg(builder, val, "neg");
            case "~":
                return LLVMBuildNot(builder, val, "xor");
            case "!":
                return LLVMBuildNot(builder, val, "not");
            default:
                throw new Error("error occurred while transformUnary: " + node.operator());
        }
    }

    public LLVMValueRef setAlloca(LLVMModuleRef module, LLVMBuilderRef builder, ExprNode expr, Scope scope) {
        if (expr instanceof AssignNode) {
            VariableNode lhs = (VariableNode) ((AssignNode) expr).lhs();
            LLVMValueRef var = scope.getAlloca(lhs.name());
            ExprNode rhs = ((AssignNode) expr).rhs();
            if (rhs instanceof IntegerLiteralNode) {
                LLVMValueRef var1 = exprToLLVM(module, builder, rhs, scope);
                LLVMBuildStore(builder, var1, var);
            } else if (rhs instanceof UnaryOpNode) {

            }
        } else if (expr instanceof VariableNode) {

        }
        return null;
    }

    public LLVMValueRef buildAlloca(LLVMModuleRef module, LLVMBuilderRef builder, DefinedVariable var, Scope scope) throws SemanticException {
        if (scope.getAlloca(var.name()) != null) {
            return scope.getAlloca(var.name());
        } else {
            LLVMTypeRef varType = typeToLLVMType(var.typeNode().type());
            LLVMValueRef alloca = LLVMBuildAlloca(builder, varType, var.name());
            scope.putAlloca(var.name(), alloca);
            return alloca;
        }
    }


    private static long constantFold(IntegerLiteralNode l, String operator, IntegerLiteralNode r) {
        long L = l.value();
        long R = r.value();
        switch (operator) {
            case "+":
                return L + R;
            case "-":
                return L - R;
            case "*":
                return L * R;
            case "/":
                return L / R;
            case "%":
                return L % R;
        }
        throw new Error("error occurred while fold constant: " + L + operator + R);
    }


    private static LLVMValueRef compileBinaryOp(LLVMModuleRef module, LLVMBuilderRef builder,
                                                LLVMValueRef right, LLVMValueRef left, String operator, Scope scope) {
        Op op = Op.internBinary(operator, false);
        LLVMValueRef ret = null;
        switch (op) {
            case ADD:
                ret = LLVMBuildAdd(builder, left, right, "add");
                break;
            case SUB:
                ret = LLVMBuildSub(builder, left, right, "sub");
                break;
            case MUL:
                ret = LLVMBuildMul(builder, left, right, "mul");
                break;
            case S_DIV:
                ret = LLVMBuildSDiv(builder, left, right, "s_div");
                break;
            case U_DIV:
                ret = LLVMBuildUDiv(builder, left, right, "u_div");
                break;
            case U_MOD:
                ret = LLVMBuildURem(builder,left,right,"u_mod");
                break;
            case S_MOD:
                ret = LLVMBuildSRem(builder,left,right,"s_mod");
                break;
            case BIT_AND:
                ret = LLVMBuildAnd(builder, left,right,"and");
                break;
            case BIT_OR:
                ret = LLVMBuildOr(builder, left,right,"or");
                break;
            case BIT_XOR:
                ret = LLVMBuildXor(builder, left,right,"xor");
                break;
            case BIT_LSHIFT:
                ret = LLVMBuildShl(builder, left, right, "shl");
                break;
            case BIT_RSHIFT:
                ret = LLVMBuildLShr(builder, left, right, "shr");
                break;
            default:
                throw new Error("unknown binary operator: " + op);
        }

        return ret;

    }


    private LLVMValueRef definedVariablesGenerate(LLVMModuleRef module, LLVMBuilderRef builder, DefinedVariable var, Scope scope) throws SemanticException {
        LLVMValueRef ret;
        LLVMTypeRef varType = typeToLLVMType(var.typeNode().type());
        if (scope.isToplevel()) {
            LLVMValueRef llvmVar = LLVMAddGlobal(module, varType, var.name());
//            LLVMSetLinkage(llvmVar, LLVMExternalLinkage);
            if (var.hasInitializer()) {
                ExprNode init = var.initializer();
                LLVMValueRef llvmInitVal = constToLLVMValue(builder, init);
                LLVMSetInitializer(llvmVar, llvmInitVal);
            }
            ret = llvmVar;
        } else {
            LLVMValueRef localVar = buildAlloca(module, builder, var, scope);
            if (var.hasInitializer()) {
                IntegerLiteralNode initExpr = (IntegerLiteralNode) var.initializer();
                long value = initExpr.value();
                LLVMBuildStore(builder, LLVMConstInt(selectSize(initExpr.type()), value, signed(initExpr)), localVar);
            }
            ret = localVar;
        }
        return ret;
    }


    private LLVMTypeRef typeToLLVMType(Type type) {
        if (type instanceof IntegerType) {
            return selectSize(type);
        } else if (type instanceof PointerType) {
            Type baseType = type.baseType();
            LLVMTypeRef baseLLVMType = typeToLLVMType(baseType);
            return LLVMPointerType(baseLLVMType, 0);
        } else if (type instanceof ArrayType) {
            Type elementType = type.baseType();
            long numElements = ((ArrayType) type).length();
            LLVMTypeRef elementLLVMType = typeToLLVMType(elementType);
            return LLVMArrayType(elementLLVMType, (int) numElements);
        } else if (type instanceof VoidType) {
            return LLVMVoidType();
        } else {
            throw new IllegalArgumentException("Unsupported type: " + type.getClass().getName());
        }
    }


    private LLVMValueRef constToLLVMValue(LLVMBuilderRef builder, ExprNode expr ) {
        if (expr instanceof IntegerLiteralNode) {
            IntegerLiteralNode initExpr = (IntegerLiteralNode) expr;
            long value = initExpr.value();
            return LLVMConstInt(selectSize(initExpr.type()), value, signed(initExpr));
        }
        else if (expr instanceof StringLiteralNode) {
            //TODO: Unimplemented
            StringLiteralNode str = (StringLiteralNode) expr;
            String value = str.value();
            LLVMValueRef strPtr = LLVMConstString(value, value.length(), 1);
            LLVMValueRef[] indices = {LLVMConstInt(LLVMInt32Type(), 0, 0), LLVMConstInt(LLVMInt32Type(), 0, signed(expr))};
            return LLVMBuildInBoundsGEP2(builder, LLVMPointerType(LLVMInt8Type(), 0), strPtr, new PointerPointer(indices), 2, "");
        }
        else if (expr instanceof CastNode){
            Type type = expr.type();
            long value = ((IntegerLiteralNode) ((CastNode) expr).expr()).value();
            return LLVMConstInt(selectSize(type), value, signed(expr));
        }
        else {
            throw new IllegalArgumentException("Unsupported constant type: " + expr.getClass().getName());
        }
    }


    private LLVMTypeRef[] typesToLLVMTypes(List<Parameter> parameter, Scope scope) throws SemanticException {
        LLVMTypeRef[] llvmTypes = new LLVMTypeRef[parameter.size()];
        for (int i = 0; i < parameter.size(); i++) {
            DefinedVariable var = parameter.get(i);
            LLVMTypeRef llvmType = typeToLLVMType(var.type());

            scope.putScopeParam(var.name(), i);
            // If the variable is an array, wrap the LLVM type in a pointer type
            if (var.type() instanceof ArrayType) {
                llvmType = LLVMPointerType(llvmType, 0);
            }
            llvmTypes[i] = llvmType;
        }
        return llvmTypes;
    }

    private LLVMValueRef[] argsToLLVMValue(LLVMModuleRef module, LLVMBuilderRef builder, List<ExprNode> args, Scope scope) {
        LLVMValueRef[] llvmTypes = new LLVMValueRef[args.size()];
        for (int i = 0; i < args.size(); i++) {
            ExprNode exprNode = args.get(i);
            LLVMValueRef argVal = null;
            if (args.get(i) instanceof VariableNode) {
                argVal = scope.getAlloca(((VariableNode) args.get(i)).name());

            } else if (args.get(i) instanceof IntegerLiteralNode) {
                argVal = exprToLLVM(module, builder, exprNode, scope);

            }
            llvmTypes[i] = argVal;
        }
        return llvmTypes;
    }


    public static int getICmpOp(String operator) {
        switch (operator) {
            case "<":
                return LLVMIntSLT;
            case "<=":
                return LLVMIntSLE;
            case ">=":
                return LLVMIntSGE;
            case ">":
                return LLVMIntSGT;
            case "==":
                return LLVMIntEQ;
            case "!=":
                return LLVMIntNE;
        }
        return 0;
    }


    private LLVMTypeRef selectSize(Type type) {
        switch ((int) type.size()){
            case 4:
                return LLVMInt32Type();
            case 2:
                return LLVMInt16Type();


            case 1:
                return LLVMInt8Type();
        }
        return null;
    }

}
