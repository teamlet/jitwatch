/*
 * Copyright (c) 2013, 2014 Chris Newland.
 * Licensed under https://github.com/AdoptOpenJDK/jitwatch/blob/master/LICENSE-BSD
 * Instructions: https://github.com/AdoptOpenJDK/jitwatch/wiki
 */
package org.adoptopenjdk.jitwatch.model;

import java.util.List;
import java.util.Map;

import org.adoptopenjdk.jitwatch.model.assembly.AssemblyMethod;
import org.adoptopenjdk.jitwatch.model.bytecode.BytecodeInstruction;
import org.adoptopenjdk.jitwatch.model.bytecode.MemberBytecode;

public interface IMetaMember
{
	MetaClass getMetaClass();
	
	MemberBytecode getMemberBytecode();
	
	List<BytecodeInstruction> getInstructions();

	void addJournalEntry(Tag entry);
	Journal getJournal();

	String getQueuedAttribute(String key);
	List<String> getQueuedAttributes();
	void setQueuedAttributes(Map<String, String> queuedAttributes);
	boolean isQueued();

	//TODO split task and nmethod attrs?
	void setCompiledAttributes(Map<String, String> compiledAttributes);
	void addCompiledAttributes(Map<String, String> additionalAttrs);
	String getCompiledAttribute(String key);
	List<String> getCompiledAttributes();
	void addCompiledAttribute(String key, String value);
	boolean isCompiled();

	String toStringUnqualifiedMethodName(boolean fqParamTypes);

	String getMemberName();
	String getFullyQualifiedMemberName();
	String getAbbreviatedFullyQualifiedMemberName();

	int getModifier();
	String getModifierString();
	String getReturnTypeName();
	String[] getParamTypeNames();

	boolean matchesSignature(MemberSignatureParts msp, boolean matchTypesExactly);

	AssemblyMethod getAssembly();

	void setAssembly(AssemblyMethod asmMethod);

	String getSignatureRegEx();
}