//
// File: Type.java
//
// UK Crown Copyright (c) 2006. All Rights Reserved.
//
package org.xtuml.masl.translate.main;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xtuml.masl.cppgen.BinaryExpression;
import org.xtuml.masl.cppgen.BinaryOperator;
import org.xtuml.masl.cppgen.CodeFile;
import org.xtuml.masl.cppgen.Expression;
import org.xtuml.masl.cppgen.FundamentalType;
import org.xtuml.masl.cppgen.Literal;
import org.xtuml.masl.cppgen.Std;
import org.xtuml.masl.cppgen.TypeUsage;
import org.xtuml.masl.cppgen.TypedefType;
import org.xtuml.masl.metamodel.expression.RangeExpression;
import org.xtuml.masl.metamodel.object.ObjectDeclaration;
import org.xtuml.masl.metamodel.type.AnonymousStructure;
import org.xtuml.masl.metamodel.type.ArrayType;
import org.xtuml.masl.metamodel.type.BagType;
import org.xtuml.masl.metamodel.type.BasicType;
import org.xtuml.masl.metamodel.type.BuiltinType;
import org.xtuml.masl.metamodel.type.CollectionType;
import org.xtuml.masl.metamodel.type.ConstrainedType;
import org.xtuml.masl.metamodel.type.DictionaryType;
import org.xtuml.masl.metamodel.type.EnumerateType;
import org.xtuml.masl.metamodel.type.InstanceType;
import org.xtuml.masl.metamodel.type.SequenceType;
import org.xtuml.masl.metamodel.type.SetType;
import org.xtuml.masl.metamodel.type.StructureType;
import org.xtuml.masl.metamodel.type.TypeDeclaration;
import org.xtuml.masl.metamodel.type.TypeDefinition;
import org.xtuml.masl.metamodel.type.UserDefinedType;
import org.xtuml.masl.metamodel.type.TypeDefinition.ActualType;
import org.xtuml.masl.translate.main.expression.ExpressionTranslator;


public class Types
{

  public final static String     FILE_NAME     = "types";
  public final static Types      instance      = new Types();

  static public Types getInstance ()
  {
    return instance;
  }

  private Types ()
  {
  }

  private final Map<TypeDeclaration, TypeUsage>             types   = new HashMap<TypeDeclaration, TypeUsage>();
  private final Map<TypeDeclaration, EnumerationTranslator> enums   = new HashMap<TypeDeclaration, EnumerationTranslator>();
  private final Map<TypeDeclaration, Structure>             structs = new HashMap<TypeDeclaration, Structure>();

  TypeUsage defineType ( final TypeDeclaration declaration )
  {
    TypeUsage result = types.get(declaration);
    if ( result != null )
    {
      return result;
    }

    TypeDefinition definition = declaration.getTypeDefinition();


    if ( definition instanceof BasicType )
    {
      final TypeUsage aliasFor = getType((BasicType)definition);
      final TypedefType typedef = new TypedefType(Mangler.mangleName(declaration),
                                                  DomainNamespace.get(declaration.getDomain()),
                                                  aliasFor);
      result = new TypeUsage(typedef);
      types.put(declaration, result);

      // Put collection or dictionary definitions in with value type
      while ( definition instanceof CollectionType || definition instanceof DictionaryType )
      {
        if ( definition instanceof CollectionType )
        {
          definition = ((CollectionType)definition).getContainedType();
        }
        else
        {
          definition = ((DictionaryType)definition).getValueType();
        }
      }


      CodeFile header = getType((BasicType)definition).getType().getDeclaredIn();

      if ( header == null
           || definition.getTypeDeclaration() == null
           || !declaration.getDomain().equals(definition.getTypeDeclaration().getDomain()) )
      {
        header = DomainTranslator.getInstance(declaration.getDomain()).getTypeHeaderFile(declaration.getVisibility());
      }
      header.addTypedefDeclaration(typedef);
    }
    else if ( definition instanceof EnumerateType )
    {
      final EnumerationTranslator enumeration = new EnumerationTranslator((EnumerateType)definition);
      result = enumeration.getType();
      types.put(declaration, result);
      enums.put(declaration, enumeration);
    }
    else if ( definition instanceof StructureType )
    {
      final Structure struct = new Structure(declaration);
      result = struct.getType();
      types.put(declaration, result);
      structs.put(declaration, struct);
      struct.translate();
    }
    else if ( definition instanceof ConstrainedType )
    {
      // TODO add constraints and checking etc
      final BasicType fullType = ((ConstrainedType)definition).getFullType();

      final TypeUsage aliasFor = getType(fullType);
      final TypedefType typedef = new TypedefType(Mangler.mangleName(declaration),
                                                  DomainNamespace.get(declaration.getDomain()),
                                                  aliasFor);
      result = new TypeUsage(typedef);
      types.put(declaration, result);

      CodeFile header = aliasFor.getType().getDeclaredIn();


      if ( header == null
           || fullType.getTypeDeclaration() == null
           || !declaration.getDomain().equals(fullType.getTypeDeclaration().getDomain()) )
      {
        header = DomainTranslator.getInstance(declaration.getDomain()).getTypeHeaderFile(declaration.getVisibility());
      }
      header.addTypedefDeclaration(typedef);
    }
    else
    {
      result = new TypeUsage(org.xtuml.masl.cppgen.FundamentalType.VOID, TypeUsage.Pointer);
      types.put(declaration, result);
    }
    return result;
  }

  /**
   *

   * @return
   */
  public CodeFile getSourceFile ( final TypeDeclaration declaration )
  {
    CodeFile sourceFile = null;
    final TypeDefinition definition = declaration.getTypeDefinition();

    if ( definition instanceof BasicType ||
         definition instanceof ConstrainedType )
    {
      // no source file defined
    }
    else if ( definition instanceof EnumerateType )
    {
      final EnumerationTranslator enumTranslator = enums.get(declaration);
      sourceFile = enumTranslator.bodyFile;
    }
    else if ( definition instanceof StructureType )
    {
      final Structure struct = structs.get(declaration);
      sourceFile = struct.getBodyFile();
    }
    return sourceFile;
  }

  public EnumerationTranslator getEnumerateTranslator ( final TypeDeclaration type )
  {
    return enums.get(type);
  }


  public Structure getStructureTranslator ( final TypeDeclaration type )
  {
    return structs.get(type);
  }

  public TypeUsage getType ( final BasicType type )
  {
    if ( type == null )
    {
      return new TypeUsage(org.xtuml.masl.cppgen.FundamentalType.VOID);
    }
    else if ( type instanceof UserDefinedType )
    {
      return getUserDefinedType((UserDefinedType)type);
    }
    else if ( type instanceof BuiltinType )
    {
      return getBuiltinType((BuiltinType)type);
    }
    else if ( type instanceof InstanceType )
    {
      final InstanceType instance = (InstanceType)type;
      final ObjectDeclaration objDec = instance.getObjectDeclaration();
      return DomainTranslator.getInstance(instance.getObjectDeclaration().getDomain()).getObjectTranslator(objDec).getPointerType();
    }
    else if ( type instanceof DictionaryType )
    {
      final DictionaryType dictionary = (DictionaryType)type;
      return new TypeUsage(Architecture.dictionary(getType(dictionary.getKeyType()), getType(dictionary.getValueType())));
    }
    else if ( type instanceof CollectionType )
    {
      final CollectionType collection = (CollectionType)type;
      final BasicType contained = collection.getContainedType();
      final TypeUsage cppContained = getType(contained);
      if ( type instanceof SequenceType )
      {
        return new TypeUsage(Architecture.sequence(cppContained));
      }
      else if ( type instanceof SetType )
      {
        return new TypeUsage(Architecture.set(cppContained));
      }
      else if ( type instanceof BagType )
      {
        return new TypeUsage(Architecture.bag(cppContained));
      }
      else if ( type instanceof ArrayType )
      {
        final RangeExpression range = ((ArrayType)type).getRange();

        final Expression min = ExpressionTranslator.createTranslator(range.getMin(), null).getReadExpression();
        final Expression max = ExpressionTranslator.createTranslator(range.getMax(), null).getReadExpression();

        return new TypeUsage(Boost.array(cppContained, new BinaryExpression(new BinaryExpression(max, BinaryOperator.MINUS, min),
                                                                      BinaryOperator.PLUS,
                                                                      Literal.ONE)));

      }
      else
      {
        throw new UnsupportedOperationException("Unrecognised CollectionType " + type.getClass().getName());
      }
    }
    else if ( type instanceof AnonymousStructure )
    {
      final List<TypeUsage> tupleTypes = new ArrayList<TypeUsage>();

      for ( final BasicType element : ((AnonymousStructure)type).getElements() )
      {
        tupleTypes.add(Types.getInstance().getType(element));
      }

      return new BigTuple(tupleTypes).getTupleType();

    }
    else
    {
      return new TypeUsage(org.xtuml.masl.cppgen.FundamentalType.VOID, TypeUsage.Pointer);
    }
  }

  private static Map<ActualType, TypeUsage> builtinTypes = new EnumMap<ActualType, TypeUsage>(ActualType.class);

  static
  {
    builtinTypes.put(ActualType.STRING, new TypeUsage(Architecture.stringClass));
    builtinTypes.put(ActualType.INTEGER, new TypeUsage(Std.int64));
    builtinTypes.put(ActualType.SMALL_INTEGER, new TypeUsage(Std.int32));
    builtinTypes.put(ActualType.DURATION, new TypeUsage(Architecture.Duration.durationClass));
    builtinTypes.put(ActualType.TIMESTAMP, new TypeUsage(Architecture.Timestamp.timestampClass));
    builtinTypes.put(ActualType.BOOLEAN, new TypeUsage(org.xtuml.masl.cppgen.FundamentalType.BOOL));
    builtinTypes.put(ActualType.REAL, new TypeUsage(org.xtuml.masl.cppgen.FundamentalType.DOUBLE));
    builtinTypes.put(ActualType.BYTE, new TypeUsage(Std.uint8));
    builtinTypes.put(ActualType.CHARACTER, new TypeUsage(org.xtuml.masl.cppgen.FundamentalType.CHAR));
    builtinTypes.put(ActualType.DEVICE, new TypeUsage(Architecture.deviceClass));
    builtinTypes.put(ActualType.WCHARACTER, new TypeUsage(org.xtuml.masl.cppgen.FundamentalType.CHAR));
    builtinTypes.put(ActualType.WSTRING, new TypeUsage(Architecture.stringClass));
    builtinTypes.put(ActualType.EVENT, new TypeUsage(Architecture.event.getEventPtr()));
    builtinTypes.put(ActualType.TIMER, new TypeUsage(Architecture.Timer.timerHandle));
    builtinTypes.put(ActualType.ANY_INSTANCE, new TypeUsage(Architecture.objectPtr(new TypeUsage(FundamentalType.VOID))));
  }

  private TypeUsage getBuiltinType ( final BuiltinType type )
  {
    return builtinTypes.get(type.getActualType());
  }

  private TypeUsage getUserDefinedType ( final UserDefinedType type )
  {
    return defineType(type.getTypeDeclaration());
  }

}
