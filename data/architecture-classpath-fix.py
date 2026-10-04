from pathlib import Path
p=Path('data-ops-boot/src/test/java/io/yak/ops/boot/architecture/PluginContractClasspathTest.java');s=p.read_text(encoding='utf-8');s=s.replace('import java.util.regex.Pattern;','import java.util.regex.Pattern;\nimport java.util.jar.JarFile;')
s=s.replace('      boolean contract = normalized.endsWith', '''      boolean contractJar = normalized.matches(".*/(?:data-ops-spi|data-ops-plugin-[^/]+-api)-[0-9][^/]*\\\\.jar");
      boolean supportJar = normalized.matches(".*/(?:data-ops-common|data-common|data-schedule-api)-[0-9][^/]*\\\\.jar");
      boolean contract = contractJar || normalized.endsWith''')
s=s.replace('      boolean support = normalized.endsWith','      boolean support = supportJar || normalized.endsWith')
start=s.index('        try (var classes = Files.walk(root)) {');end=s.index('\n      }\n    }',start)
s=s[:start]+'''        List<String> names;
        if (Files.isDirectory(root)) {
          try (var classes = Files.walk(root)) {
            names = classes.filter(path -> path.toString().endsWith(".class"))
                .map(path -> root.relativize(path).toString().replace('\\\\', '/')).toList();
          }
        } else {
          try (JarFile jar = new JarFile(root.toFile())) {
            names = jar.stream().map(entry -> entry.getName()).filter(name -> name.endsWith(".class")).toList();
          }
        }
        for (String entry : names) {
          String name = entry.replace('/', '.').replaceAll("\\\\.class$", "");
          Class<?> type = Class.forName(name, false, loader);
          // Resolving generic types catches leaks that simply loading the class would miss.
          for (var method : type.getDeclaredMethods()) {
            method.getGenericReturnType().getTypeName();
            for (var parameter : method.getGenericParameterTypes()) parameter.getTypeName();
          }
          for (var field : type.getDeclaredFields()) field.getGenericType().getTypeName();
          for (var constructor : type.getDeclaredConstructors()) constructor.getGenericParameterTypes();
        }'''+s[end:];p.write_text(s,encoding='utf-8')
