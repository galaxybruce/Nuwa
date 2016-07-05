package cn.jiajixin.nuwa.util

class NuwaSetUtils {

    public static boolean isExcluded(String path, Set<String> excludePackage, Set<String> excludeClass) {
        for (String exclude:excludeClass){
            if(path.endsWith(exclude)) {
                return  true;
            }
        }
        for (String exclude:excludePackage){
            if(path.startsWith(exclude)) {
                return  true;
            }
        }

        return false;
    }

    public static boolean isIncluded(String path, Set<String> includePackage) {
        if (includePackage.size() == 0) {
            return true
        }

        for (String include:includePackage){
            if(path.startsWith(include)) {
                return  true;
            }
        }

        return false;
    }

    /**
     * add by bruce.zhang
     * @param path
     * @param includeJar
     * @return
     */
    public static boolean isIncludedJar(String path, Set<String> includeJar) {
        if (includeJar.size() == 0) {
            return true
        }

        for (String include:includeJar){
            if(path.startsWith(include)) {
                return  true;
            }
        }

        return false;
    }
}
