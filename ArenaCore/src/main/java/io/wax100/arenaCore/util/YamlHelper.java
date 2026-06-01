package io.wax100.arenaCore.util;


import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * YAML ファイル操作のユーティリティ。
 */
public final class YamlHelper {

    private YamlHelper() {}

    /**
     * 指定ディレクトリ内の {@code .yml} ファイル名（拡張子除去済み）を
     * ソート済みリストで返す。
     *
     * @param directory 検索対象ディレクトリ（null不可）
     * @return ファイル名リスト（拡張子なし、ソート済み）
     */
    public static List<String> listYmlNames(File directory) {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) return List.of();

        List<String> names = new ArrayList<>();
        for (File file : files) {
            String fileName = file.getName();
            names.add(fileName.substring(0, fileName.length() - 4));
        }
        Collections.sort(names);
        return names;
    }
}
