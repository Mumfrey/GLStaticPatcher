package com.mumfrey.mcptools;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;

public class GLStaticPatcher implements FilenameFilter
{
	public static final String VERSION = "1.0.0";
	
	public static final Pattern importLinePattern = Pattern.compile("import (org\\.lwjgl\\.(?:opengl|util\\.glu)\\.(Project|GL\\w+));");
	
	private File baseDir;
	
	public GLStaticPatcher(File baseDir) throws IOException
	{
		System.out.println("GLStaticPatcher v" + VERSION);
		
		this.baseDir = baseDir.getCanonicalFile();
	}

	public void run()
	{
		System.out.println("Running in " + baseDir.getAbsolutePath());
		this.processDir(this.baseDir);
	}

	@Override
	public boolean accept(File dir, String name)
	{
		return dir.isDirectory() || (dir.isFile() && name.endsWith(".java"));
	}

	private void processDir(File dir)
	{
		for (File file : dir.listFiles(this))
		{
			if (file.isDirectory())
				this.processDir(file);
			else
				this.processFile(file);
		}
	}

	private void processFile(File file)
	{
		try
		{
			String contents = Files.readLines(file, Charsets.UTF_8, new LineProcessor<String>()
			{
				private StringBuilder stringBuilder = new StringBuilder();
				
				private Set<String> imports = new HashSet<String>();
				
				@Override
				public String getResult()
				{
					return this.stringBuilder.toString();
				}

				@Override
				public boolean processLine(String line) throws IOException
				{
					Matcher importLinePatternMatcher = GLStaticPatcher.importLinePattern.matcher(line);
					while (importLinePatternMatcher.find())
					{
						this.imports.add(importLinePatternMatcher.group(2) + "\\.(?=[a-zA-Z])");
						String staticImport = String.format("import static %s.*;", importLinePatternMatcher.group(1));
						line = line.substring(0, importLinePatternMatcher.start()) + staticImport + line.substring(importLinePatternMatcher.end());
						importLinePatternMatcher.reset(line);
					}
					
					for (String imp : this.imports)
						line = line.replaceAll(imp, "");
					
					this.stringBuilder.append(line).append("\r\n");
					
					return true;
				}
			});
			
			Files.write(contents, file, Charsets.UTF_8);
		}
		catch (IOException ex)
		{
			ex.printStackTrace();
		}
	}

	public static void main(String[] args)
	{
		try
		{
			if (args.length > 0)
			{
				File baseDir = new File(args[0]);
				if (baseDir.exists() && baseDir.isDirectory())
				{
					GLStaticPatcher patcher = new GLStaticPatcher(baseDir);
					patcher.run();
				}
				else
				{
					System.out.println("Error: " + baseDir.getAbsolutePath() + " does not exist or is not a directory");
					System.exit(1);
				}
			}
			else
			{
				GLStaticPatcher patcher = new GLStaticPatcher(new File(System.getProperty("user.dir")));
				patcher.run();
			}
		}
		catch (IOException ex)
		{
			ex.printStackTrace();
			System.exit(1);
		}
	}
}
