package io.wax100.arenaCore.command;

import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

/**
 * 闘技場サブコマンドの基底インターフェース。
 *
 * <p>各サブコマンドはこのインターフェースを実装し、
 * {@link ArenaCommand} がディスパッチする。
 */
public interface SubCommand {

    /**
     * サブコマンドを実行する。
     *
     * @param sender コマンド送信者
     * @param args   サブコマンド名以降の引数（例: {@code /arena team add X Y} → {@code ["add","X","Y"]}）
     */
    void execute(CommandSender sender, String[] args);

    /**
     * タブ補完候補を返す。
     *
     * @param sender コマンド送信者
     * @param args   サブコマンド名以降の引数
     * @return 補完候補リスト
     */
    default List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    /**
     * 使い方メッセージを返す。
     *
     * @return 使い方文字列
     */
    String getUsage();
}
