/*
// $Id$
// Farrago is a relational database management system.
// Copyright (C) 2004-2004 John V. Sichi.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public License
// as published by the Free Software Foundation; either version 2.1
// of the License, or (at your option) any later version.
// 
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Lesser General Public License for more details.
// 
// You should have received a copy of the GNU Lesser General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/
package net.sf.farrago.query;

import net.sf.farrago.fem.sql2003.*;
import net.sf.farrago.session.*;
import net.sf.farrago.plugin.*;
import net.sf.farrago.catalog.*;
import net.sf.farrago.resource.*;
import net.sf.farrago.util.*;
import net.sf.farrago.ojrex.*;

import org.eigenbase.sql.*;
import org.eigenbase.sql.type.*;
import org.eigenbase.reltype.*;
import org.eigenbase.rex.*;
import org.eigenbase.util.*;
import org.eigenbase.oj.rex.*;

import openjava.mop.*;
import openjava.ptree.*;

import java.lang.reflect.*;
import java.util.*;

import java.util.List;

/**
 * FarragoUserDefinedRoutine extends {@link SqlFunction} with a
 * repository reference to a specific user-defined routine.
 *
 * @author John V. Sichi
 * @version $Id$
 */
public class FarragoUserDefinedRoutine
    extends SqlFunction
    implements OJRexImplementor
{
    private final FemRoutine routine;

    private final RelDataType returnType;
    
    private final RelDataType [] paramTypes;

    private final FarragoSessionStmtValidator stmtValidator;

    public FarragoUserDefinedRoutine(
        FarragoSessionStmtValidator stmtValidator,
        FemRoutine routine,
        RelDataType returnType,
        RelDataType [] paramTypes)
    {
        super(
            routine.getName(),
            SqlKind.Function,
            new ReturnTypeInferenceImpl.FixedReturnTypeInference(returnType),
            new ExplicitParamInference(paramTypes),
            new AssignableOperandsTypeChecking(paramTypes),
            SqlFunction.SqlFuncTypeName.User);
        this.stmtValidator = stmtValidator;
        this.routine = routine;
        this.returnType = returnType;
        this.paramTypes = paramTypes;
    }
    
    public FemRoutine getFemRoutine()
    {
        return routine;
    }

    public RelDataType getReturnType()
    {
        return returnType;
    }

    public RelDataType [] getParamTypes()
    {
        return paramTypes;
    }

    /**
     * Uses an external Java routine definition plus reflection to find a
     * corresponding Java method.
     *
     * @return Java method
     *
     * @exception SqlValidatorException if matching Java method could
     * not be found
     */
    public Method getJavaMethod()
        throws SqlValidatorException
    {
        FarragoRepos repos = stmtValidator.getRepos();
        
        String externalName = routine.getExternalName();
        // TODO jvs 11-Jan-2005:  JAR support, and move some of this
        // code to FarragoPluginCache
        String fullMethodName;
        if (!externalName.startsWith(FarragoPluginCache.LIBRARY_CLASS_PREFIX)) {
            // force error below
            fullMethodName = "";
        } else {
            fullMethodName = externalName.substring(
                FarragoPluginCache.LIBRARY_CLASS_PREFIX.length());
        }
        int iLeftParen = fullMethodName.indexOf('(');
        String classPlusMethodName;
        if (iLeftParen == -1) {
            classPlusMethodName = fullMethodName;
        } else {
            classPlusMethodName = fullMethodName.substring(0, iLeftParen);
        }
        int iLastDot = classPlusMethodName.lastIndexOf('.');
        if (iLastDot == -1) {
            throw FarragoResource.instance().
                newValidatorRoutineInvalidJavaMethod(
                    repos.getLocalizedObjectName(routine),
                    repos.getLocalizedObjectName(externalName));
        }
        String javaClassName = classPlusMethodName.substring(0, iLastDot);
        String javaMethodName = classPlusMethodName.substring(iLastDot + 1);
        int nParams = FarragoCatalogUtil.getRoutineParamCount(routine);
        Class [] javaParamClasses = new Class[nParams];
        if (iLeftParen == -1) {
            List params = routine.getParameter();
            for (int i = 0; i < nParams; ++i) {
                RelDataType type = paramTypes[i];
                javaParamClasses[i] =
                    stmtValidator.getTypeFactory().getClassForJavaParamStyle(
                        type);
                if (javaParamClasses[i] == null) {
                    throw Util.needToImplement(type);
                }
            }
        } else {
            int iNameStart = iLeftParen + 1;
            boolean last = false;
            int i = 0;
            for (; (i < nParams) && !last; ++i) {
                int iComma = fullMethodName.indexOf(',', iNameStart);
                if (iComma == -1) {
                    iComma = fullMethodName.indexOf(')', iNameStart);
                    // TODO:  assert nothing past rparen
                    if (iComma == -1) {
                        throw FarragoResource.instance().
                            newValidatorRoutineInvalidJavaMethod(
                                repos.getLocalizedObjectName(routine),
                                repos.getLocalizedObjectName(externalName));
                    }
                    last = true;
                }
                String typeName = fullMethodName.substring(
                    iNameStart, iComma);
                Class paramClass;
                try {
                    paramClass = ReflectUtil.getClassForName(typeName);
                } catch (Exception ex) {
                    // TODO jvs 16-Jan-2005:  more specific err msg
                    throw FarragoResource.instance().newPluginInitFailed(
                        javaClassName, ex);
                }
                javaParamClasses[i] = paramClass;
                iNameStart = iComma + 1;
            }
            if (!last || (i != nParams)) {
                // TODO jvs 16-Jan-2005:  specific err msg for mismatch
                // between number of SQL routine parameters and number of
                // Java method parameters
                throw FarragoResource.instance().newPluginInitFailed(
                    javaClassName);
            }
        }
        
        Class javaClass;
        try {
            javaClass = Class.forName(javaClassName);
        } catch (Exception ex) {
            throw FarragoResource.instance().newPluginInitFailed(
                javaClassName, ex);
        }
        
        String javaUnmangledMethodName = ReflectUtil.getUnmangledMethodName(
            javaClass,
            javaMethodName,
            javaParamClasses);
        
        Method javaMethod;
        try {
            javaMethod = javaClass.getMethod(javaMethodName, javaParamClasses);
        } catch (NoSuchMethodException ex) {
            throw FarragoResource.instance().
                newValidatorRoutineJavaMethodNotFound(
                    repos.getLocalizedObjectName(routine),
                    repos.getLocalizedObjectName(javaUnmangledMethodName));
        } catch (Exception ex) {
            throw FarragoResource.instance().newPluginInitFailed(
                javaClassName, ex);
        }

        int modifiers = javaMethod.getModifiers();
        if (!Modifier.isStatic(modifiers) || !Modifier.isPublic(modifiers)) {
            throw FarragoResource.instance().
                newValidatorRoutineJavaMethodNotPublicStatic(
                    repos.getLocalizedObjectName(routine),
                    repos.getLocalizedObjectName(javaUnmangledMethodName));
        }

        // verify compatibility between SQL and Java types for return type
        // and all parameters
        JavaToSqlTypeConversionRules rules =
            JavaToSqlTypeConversionRules.instance();

        Class javaReturnClass = javaMethod.getReturnType();
        SqlTypeName actualReturnSqlType = rules.lookup(javaReturnClass);
        SqlTypeName declReturnSqlType = returnType.getSqlTypeName();
        if (!checkCompatibility(actualReturnSqlType, declReturnSqlType)) {
            throw FarragoResource.instance().
                newValidatorRoutineJavaReturnMismatch(
                    repos.getLocalizedObjectName(routine),
                    returnType.toString(),
                    repos.getLocalizedObjectName(javaUnmangledMethodName),
                    javaReturnClass.toString());
        }

        for (int i = 0; i < nParams; ++i) {
            Class javaParamClass = javaParamClasses[i];
            SqlTypeName actualParamSqlType = rules.lookup(javaParamClass);
            SqlTypeName declParamSqlType = paramTypes[i].getSqlTypeName();
            if (!checkCompatibility(actualParamSqlType, declParamSqlType)) {
                throw FarragoResource.instance().
                    newValidatorRoutineJavaParamMismatch(
                        repos.getLocalizedObjectName((FemRoutineParameter)
                            (routine.getParameter().get(i))),
                        repos.getLocalizedObjectName(routine),
                        paramTypes[i].toString(),
                        repos.getLocalizedObjectName(javaUnmangledMethodName),
                        javaParamClass.toString());
            }
        }

        return javaMethod;
    }

    private static boolean checkCompatibility(SqlTypeName t1, SqlTypeName t2)
    {
        if ((t1 == null) || (t2 == null)) {
            return false;
        }
        return t1.getFamily() == t2.getFamily();
    }

    // implement OJRexImplementor
    public Expression implement(
        RexToOJTranslator translator,
        RexCall call,
        Expression [] operands)
    {
        FarragoRexToOJTranslator farragoTranslator =
            (FarragoRexToOJTranslator) translator;
        
        assert(call.getOperator() == this);
        ExpressionList exprList = new ExpressionList();
        Method method;
        try {
            method = getJavaMethod();
        } catch (SqlValidatorException ex) {
            throw FarragoResource.instance().newPluginMethodMismatch(ex);
        }
        Class [] javaParams = method.getParameterTypes();
        for (int i = 0; i < operands.length; ++i) {
            Expression expr = translateOperand(
                farragoTranslator,
                operands[i],
                javaParams[i],
                call.getOperands()[i].getType(),
                paramTypes[i]);
            exprList.add(expr);
        }
        Expression callExpr = new MethodCall(
            OJClass.forClass(method.getDeclaringClass()), 
            method.getName(),
            exprList);
        RelDataType actualReturnType;
        if (method.getReturnType().isPrimitive()) {
            actualReturnType = 
                stmtValidator.getTypeFactory().createTypeWithNullability(
                    returnType,
                    false);
        } else {
            actualReturnType = 
                stmtValidator.getTypeFactory().createJavaType(
                    method.getReturnType());
        }
        return farragoTranslator.convertCastOrAssignment(
            returnType,
            actualReturnType,
            null,
            callExpr);
    }
    
    // implement OJRexImplementor
    public boolean canImplement(RexCall call)
    {
        return call.getOperator() == this;
    }

    private Expression translateOperand(
        FarragoRexToOJTranslator farragoTranslator,
        Expression argExpr,
        Class javaParamClass,
        RelDataType argType,
        RelDataType paramType)
    {
        if (javaParamClass.isPrimitive()) {
            // TODO jvs 16-Jan-2005:  specialize error message for
            // case of NULL argument detection; also optimize
            // away NULL detection when the routine is declared as
            // RETURNS NULL ON NULL INPUT
            return farragoTranslator.convertCastOrAssignment(
                stmtValidator.getTypeFactory().createTypeWithNullability(
                    paramType,
                    false),
                paramType,
                null,
                argExpr);
        } else {
            return new CastExpression(
                OJClass.forClass(javaParamClass),
                new MethodCall(
                    argExpr,
                    "getNullableData",
                    new ExpressionList()));
        }
    }
}

// End FarragoUserDefinedRoutine.java