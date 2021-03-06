/*
 * Copyright (c) 2013-2017 Chris Newland.
 * Licensed under https://github.com/AdoptOpenJDK/jitwatch/blob/master/LICENSE-BSD
 * Instructions: https://github.com/AdoptOpenJDK/jitwatch/wiki
 */
package org.adoptopenjdk.jitwatch.parser.hotspot;

import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.C_AT;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.C_OPEN_ANGLE;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.C_OPEN_SQUARE_BRACKET;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.DEBUG_LOGGING;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.LOADED;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.SKIP_BODY_TAGS;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.SKIP_HEADER_TAGS;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_AT;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_FILE_COLON;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_OPEN_ANGLE;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.S_SPACE;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.TAG_CLOSE_CDATA;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.TAG_CODE_CACHE_FULL;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.TAG_COMMAND;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.TAG_HOTSPOT_LOG_DONE;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.TAG_NMETHOD;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.TAG_OPEN_CDATA;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.TAG_OPEN_CLOSE_CDATA;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.TAG_RELEASE;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.TAG_START_COMPILE_THREAD;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.TAG_SWEEPER;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.TAG_TASK;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.TAG_TASK_QUEUED;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.TAG_TTY;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.TAG_VM_ARGUMENTS;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.TAG_VM_VERSION;
import static org.adoptopenjdk.jitwatch.core.JITWatchConstants.TAG_XML;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.List;
import java.util.Set;

import org.adoptopenjdk.jitwatch.core.IJITListener;
import org.adoptopenjdk.jitwatch.model.CodeCacheEvent.CodeCacheEventType;
import org.adoptopenjdk.jitwatch.model.NumberedLine;
import org.adoptopenjdk.jitwatch.model.Tag;
import org.adoptopenjdk.jitwatch.model.Task;
import org.adoptopenjdk.jitwatch.parser.AbstractLogParser;
import org.adoptopenjdk.jitwatch.util.StringUtil;

public class HotSpotLogParser extends AbstractLogParser
{
	public HotSpotLogParser(IJITListener jitListener)
	{
		super(jitListener);
	}

	private void checkIfErrorDialogNeeded()
	{
		if (!hasParseError)
		{
			if (!hasTraceClassLoad)
			{
				hasParseError = true;

				errorDialogTitle = "Missing VM Switch -XX:+TraceClassLoading";
				errorDialogBody = "JITWatch requires the -XX:+TraceClassLoading VM switch to be used.\nPlease recreate your log file with this switch enabled.";
			}
		}

		if (hasParseError)
		{
			errorListener.handleError(errorDialogTitle, errorDialogBody);
		}
	}

	private void parseHeaderLines()
	{
		if (DEBUG_LOGGING)
		{
			logger.debug("parseHeaderLines()");
		}

		for (NumberedLine numberedLine : splitLog.getHeaderLines())
		{
			if (!skipLine(numberedLine.getLine(), SKIP_HEADER_TAGS))
			{
				Tag tag = tagProcessor.processLine(numberedLine.getLine());

				processLineNumber = numberedLine.getLineNumber();

				if (tag != null)
				{
					handleTag(tag);
				}
			}
		}
	}

	@Override
	protected void parseLogFile()
	{
		parseHeaderLines();

		buildParsedClasspath();

		buildClassModel();

		parseLogCompilationLines();

		parseAssemblyLines();

		checkIfErrorDialogNeeded();
	}

	private void parseLogCompilationLines()
	{
		if (DEBUG_LOGGING)
		{
			logger.debug("parseLogCompilationLines()");
		}

		for (NumberedLine numberedLine : splitLog.getCompilationLines())
		{
			if (!skipLine(numberedLine.getLine(), SKIP_BODY_TAGS))
			{
				Tag tag = tagProcessor.processLine(numberedLine.getLine());

				processLineNumber = numberedLine.getLineNumber();

				if (tag != null)
				{
					handleTag(tag);
				}
			}
		}
	}

	private void parseAssemblyLines()
	{
		if (DEBUG_LOGGING)
		{
			logger.debug("parseAssemblyLines()");
		}

		for (NumberedLine numberedLine : splitLog.getAssemblyLines())
		{
			processLineNumber = numberedLine.getLineNumber();

			asmProcessor.handleLine(numberedLine.getLine());
		}

		asmProcessor.complete();

		asmProcessor.attachAssemblyToMembers(model.getPackageManager());

		asmProcessor.clear();
	}

	@Override
	protected void splitLogFile(File hotspotLog)
	{
		reading = true;

		try (BufferedReader reader = new BufferedReader(new FileReader(hotspotLog), 65536))
		{
			String currentLine = reader.readLine();

			while (reading && currentLine != null)
			{
				try
				{
					String trimmedLine = currentLine.trim();

					if (trimmedLine.length() > 0)
					{
						char firstChar = trimmedLine.charAt(0);

						if (firstChar == C_OPEN_ANGLE || firstChar == C_OPEN_SQUARE_BRACKET || firstChar == C_AT)
						{
							currentLine = trimmedLine;
						}

						handleLogLine(currentLine);
					}
				}
				catch (Exception ex)
				{
					logger.error("Exception handling: '{}'", currentLine, ex);
				}

				currentLine = reader.readLine();
			}
		}
		catch (IOException ioe)
		{
			logger.error("Exception while splitting log file", ioe);
		}
	}
	
	private boolean skipLine(final String line, final Set<String> skipSet)
	{
		boolean isSkip = false;

		for (String skip : skipSet)
		{
			if (line.startsWith(skip))
			{
				isSkip = true;
				break;
			}
		}

		return isSkip;
	}

	private void handleLogLine(final String inCurrentLine)
	{
		String currentLine = inCurrentLine;

		NumberedLine numberedLine = new NumberedLine(parseLineNumber++, currentLine);

		if (TAG_TTY.equals(currentLine))
		{
			inHeader = false;
			return;
		}
		else if (currentLine.startsWith(TAG_XML))
		{
			inHeader = true;
		}

		if (inHeader)
		{
			// HotSpot log header XML can have text nodes so consume all lines
			splitLog.addHeaderLine(numberedLine);
		}
		else
		{
			if (currentLine.startsWith(TAG_OPEN_CDATA) || currentLine.startsWith(TAG_CLOSE_CDATA)
					|| currentLine.startsWith(TAG_OPEN_CLOSE_CDATA))
			{
				// ignore, TagProcessor will recognise from <fragment> tag
			}
			else if (currentLine.startsWith(S_OPEN_ANGLE))
			{
				// After the header, XML nodes do not have text nodes
				splitLog.addCompilationLine(numberedLine);
			}
			else if (currentLine.startsWith(LOADED))
			{
				splitLog.addClassLoaderLine(numberedLine);
			}
			else if (currentLine.startsWith(S_AT))
			{
				// possible PrintCompilation was enabled as well as
				// LogCompilation?
				// jmh does this with perf annotations
				// Ignore this line
			}
			else
			{
				// need to cope with nmethod appearing on same line as last hlt
				// 0x0000 hlt <nmethod compile_id= ....

				int indexNMethod = currentLine.indexOf(S_OPEN_ANGLE + TAG_NMETHOD);

				if (indexNMethod != -1)
				{
					if (DEBUG_LOGGING)
					{
						logger.debug("detected nmethod tag mangled with assembly");
					}

					String assembly = currentLine.substring(0, indexNMethod);

					String remainder = currentLine.substring(indexNMethod);

					numberedLine.setLine(assembly);

					splitLog.addAssemblyLine(numberedLine);

					handleLogLine(remainder);
				}
				else
				{
					splitLog.addAssemblyLine(numberedLine);
				}
			}
		}
	}

	@Override
	protected void handleTag(Tag tag)
	{
		String tagName = tag.getName();

		switch (tagName)
		{
		case TAG_VM_VERSION:
			handleVmVersion(tag);
			break;

		case TAG_TASK_QUEUED:
			handleTagQueued(tag);
			break;

		case TAG_NMETHOD:
			handleTagNMethod(tag);
			break;

		case TAG_TASK:
			handleTagTask((Task) tag);
			break;

		case TAG_SWEEPER:
			storeCodeCacheEvent(CodeCacheEventType.SWEEPER, tag);
			break;

		case TAG_CODE_CACHE_FULL:
			storeCodeCacheEvent(CodeCacheEventType.CACHE_FULL, tag);
			break;

		case TAG_HOTSPOT_LOG_DONE:
			model.setEndOfLog(tag);
			break;

		case TAG_START_COMPILE_THREAD:
			handleStartCompileThread(tag);
			break;

		case TAG_VM_ARGUMENTS:
			handleTagVmArguments(tag);
			break;

		default:
			break;
		}
	}

	private void handleVmVersion(Tag tag)
	{
		String release = tag.getNamedChildren(TAG_RELEASE).get(0).getTextContent();

		model.setVmVersionRelease(release);
	}

	private void handleTagVmArguments(Tag tag)
	{
		List<Tag> tagCommandChildren = tag.getNamedChildren(TAG_COMMAND);

		if (tagCommandChildren.size() > 0)
		{
			vmCommand = tagCommandChildren.get(0).getTextContent();

			if (DEBUG_LOGGING)
			{
				logger.debug("VM Command: {}", vmCommand);
			}
		}
	}

	private void handleStartCompileThread(Tag tag)
	{
		model.getJITStats().incCompilerThreads();
	}


	private void buildParsedClasspath()
	{
		if (DEBUG_LOGGING)
		{
			logger.debug("buildParsedClasspath()");
		}

		for (NumberedLine numberedLine : splitLog.getClassLoaderLines())
		{
			buildParsedClasspath(numberedLine.getLine());
		}
	}

	private void buildClassModel()
	{
		if (DEBUG_LOGGING)
		{
			logger.debug("buildClassModel()");
		}

		for (NumberedLine numberedLine : splitLog.getClassLoaderLines())
		{
			buildClassModel(numberedLine.getLine());
		}
	}

	private void buildParsedClasspath(String inCurrentLine)
	{
		if (!hasTraceClassLoad)
		{
			hasTraceClassLoad = true;
		}

		final String FROM_SPACE = "from ";

		String originalLocation = null;

		int fromSpacePos = inCurrentLine.indexOf(FROM_SPACE);

		if (fromSpacePos != -1)
		{
			originalLocation = inCurrentLine.substring(fromSpacePos + FROM_SPACE.length(), inCurrentLine.length() - 1);
		}

		if (originalLocation != null && originalLocation.startsWith(S_FILE_COLON))
		{
			originalLocation = originalLocation.substring(S_FILE_COLON.length());

			try
			{
				originalLocation = URLDecoder.decode(originalLocation, "UTF-8");
			}
			catch (UnsupportedEncodingException e)
			{
				// ignore
			}

			getParsedClasspath().addClassLocation(originalLocation);
		}
	}

	private void buildClassModel(String inCurrentLine)
	{
		String fqClassName = StringUtil.getSubstringBetween(inCurrentLine, LOADED, S_SPACE);

		if (fqClassName != null)
		{
			addToClassModel(fqClassName);
		}
	}
}