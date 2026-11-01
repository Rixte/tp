package mishmash.testutil;

import java.net.URL;
import java.net.URLClassLoader;

//@@author Rixte
/**
 * Loads one production class with assertions disabled while sharing its dependencies with the test suite.
 * A separate copy is necessary because Java fixes a class's assertion mode when it is initialized.
 */
public final class AssertionDisabledClassLoader extends URLClassLoader {
    private final String targetClassName;

    /**
     * Creates an isolated loader without changing assertion settings for any existing class.
     *
     * @param targetClass The production class whose assertion-disabled behavior is being checked.
     */
    public AssertionDisabledClassLoader(Class<?> targetClass) {
        super(new URL[]{targetClass.getProtectionDomain().getCodeSource().getLocation()},
                targetClass.getClassLoader());
        targetClassName = targetClass.getName();
        setClassAssertionStatus(targetClassName, false);
    }

    /**
     * Loads the selected class locally; delegates all dependencies to the existing application loader.
     */
    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (!name.equals(targetClassName)) {
            return super.loadClass(name, resolve);
        }
        Class<?> loadedClass = findLoadedClass(name);
        if (loadedClass == null) {
            loadedClass = findClass(name);
        }
        if (resolve) {
            resolveClass(loadedClass);
        }
        return loadedClass;
    }
}
//@@author
