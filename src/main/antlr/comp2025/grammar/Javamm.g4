// GRAMMAR DECLARATION -------------------------------
grammar Javamm;

// HEADER SECTION ------------------------------------
@header {
    package pt.up.fe.comp2025;
}

// LEXER RULES ---------------------------------------

// Tokens for keywords
CLASS :     'class' ;
PUBLIC :    'public' ;
PRIVATE :   'private' ;
PROTECTED : 'protected' ;
STATIC :    'static' ;
FINAL :     'final' ;
RETURN :    'return' ;
INT :       'int' ;
BOOLEAN :   'boolean' ;
CHAR :      'char' ;
DOUBLE :    'double' ;
FLOAT :     'float' ;
STRING :    'String' ;
WHILE :     'while' ;
IF :        'if' ;
ELSE :      'else' ;
NEW :       'new' ;
THIS :      'this' ;
SUPER :     'super' ;
NULL :      'null' ;
TRUE :      'true' ;
FALSE :     'false' ;
MAIN :      'main';
FOR:        'for';
CONTINUE:   'continue';
ABSTRACT:   'abstract';
ASSERT:     'assert';
BREAK:      'break';
BYTE:       'byte';
CASE:       'case';
CATCH:      'catch';
DEFAULT:    'default';
ENUM:       'enum';
EXTENDS:    'extends';
IMPLEMENTS: 'implements';
FINALLY:    'finally';
IMPORT:     'import';
INSTANCEOF: 'instanceof';
INTERFACE:  'interface';
LONG:       'long';
NATIVE:     'native';
SHORT:      'short';
STRICTFP:   'strictfp';
PACKAGE:    'package';
SWITCH:     'switch';
SYNCHRONIZED: 'synchronized';
THROW:      'throw';
THROWS:      'throws';
TRANSIENT:   'transient';
TRY:         'try';
VOID:        'void';
VOLATILE:    'volatile';


// Integers and Identifiers
INTEGER :   [0] | ([1-9][0-9]*) ;                      // + allows to repeat one or more times
ID :        [a-zA-Z_$] [a-zA-Z0-9_$]* ;   // identifiers must start with a letter and must accept _, $ and numbers

// Whitespace Handling
WS : [ \t\n\r\f]+ -> skip ;

// Comment Handling
COMMENT :       '/*' .*? '*/' -> skip ;
LINE_COMMENT :  '//' ~[\r\n]* -> skip ;

// Variable arguments
VARARGS : '...' ;

// PARSER RULES -------------------------------------
program
    : importDecl* classDecl EOF
    ;

// Import declaration
importDecl
    : IMPORT importName+=ID ('.'importName+=ID)*';'
    ;

// Class declaration
classDecl
    : CLASS className=ID (EXTENDS superName=ID)? '{' varDecl* methodDecl* '}'
    ;

// Variable declaration
varDecl
    : type var=ID ';'
    ;

type locals[boolean isArray = false, boolean isVararg = false]
    : name=INT ('[' ']' {$isArray = true;})?         #IntType
    | name=INT VARARGS  {$isArray = true; $isVararg = true;}           #IntVararg
    | name=BOOLEAN ('[' ']' {$isArray = true;})?     #BoolType
    | name=STRING ('[' ']' {$isArray = true;})?      #StringType
    | name=ID                                        #IDType
    ;

// Method declaration
methodDecl locals[boolean isPublic = false, boolean isStatic = false]
    : (PUBLIC {$isPublic = true;})? (hasStatic=STATIC {$isStatic = true;})? type methodName=ID '(' (param (',' param)*)? ')' '{' varDecl* stmt* hasReturn=RETURN expr ';' '}'
    | (PUBLIC {$isPublic = true;})? (STATIC {$isStatic = true;})? VOID methodName=MAIN '(' STRING '[' ']' var=ID ')' '{' varDecl* stmt* '}'
    ;

// Parameter
param
    : type name=ID
    ;

// Statement
stmt
    : '{' stmt* '}'                                     #BlockStmt
    | IF '(' expr ')' stmt ELSE stmt                    #IfStmt
    | WHILE '(' expr ')' stmt                           #WhileStmt
    | expr ';'                                          #ExprStmt
    | var=ID '=' expr ';'                               #AssignStmt
    | var=ID '[' expr ']' '=' expr ';'                  #ArrayAssignStmt
    ;

// Expression
expr
    : NEW INT '[' expr ']'                                  #NewArray
    | NEW className=ID '(' ')'                              #NewObject
    | '(' expr ')'                                          #ParenthesesOp
    | '[' (expr (',' expr)* )? ']'                          #ArrayLiteral
    | expr '[' expr ']'                                     #ArrayIndex
    | expr '.' methodName=ID '(' (expr (',' expr)* )? ')'   #MethodCall
    | expr '.' 'length'                                     #ArrayLength
    | THIS                                                  #This
    | '!' expr                                              #LogicalNot
    | expr op='&&' expr                                     #BinaryOp
    | expr op=('<' | '>') expr                              #BinaryOp
    | expr op=('*' | '/') expr                              #BinaryOp
    | expr op=('+' | '-') expr                              #BinaryOp
    | value=INTEGER                                         #Integer
    | TRUE                                                  #True
    | FALSE                                                 #False
    | var=ID                                                #Identifier
    ;
