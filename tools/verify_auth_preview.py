"""执行真实登录／注册页面与测试入口，验证本地预览不发包、不断开连接；不启动游戏。"""

from pathlib import Path
import argparse
import json
import os
import subprocess
import tempfile
import verify_yzui_menu as fixtures

ROOT, CLIENT = fixtures.ROOT, fixtures.CLIENT
STUBS, stub = fixtures.STUBS, fixtures.stub
C, P = fixtures.C, fixtures.P


def adapt_stubs():
    screen = "net/minecraft/client/gui/screens/Screen.java"
    STUBS[screen] = STUBS[screen].replace("public Screen(Component c){}", """
    protected final Component title;
    public Screen(Component c){title=c;}
    protected <T extends AbstractWidget> T addRenderableWidget(T w){return addWidget(w);}
    public boolean charTyped(CharacterEvent event){return false;}
    public void removed(){}
""")
    stub("net.minecraft.client.input.CharacterEvent", "public record CharacterEvent(int codepoint) {}")
    stub("net.minecraft.client.gui.components.EditBox", """
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
public class EditBox extends AbstractWidget {
    private String value="";
    public EditBox(Font f,int x,int y,int w,int h,Component label){super(x,y,w,h,label);}
    public void setValue(String text){value=text;}
    public String getValue(){return value;}
    public void setEditable(boolean value){}
    public void setCanLoseFocus(boolean value){}
    public void setTextColor(int value){}
    public void setMaxLength(int value){}
    public void setHint(Component value){}
    public boolean charTyped(CharacterEvent event){return false;}
}
""")
    mc = "net/minecraft/client/Minecraft.java"
    STUBS[mc] = STUBS[mc].replace("public final Font font", "public int disconnects;\n    public final Font font")
    STUBS[mc] = STUBS[mc].replace("public void setScreenAndShow", """
    public void disconnectFromWorld(net.minecraft.network.chat.Component c){disconnects++;}
    public User getUser(){return new User();}
    public static class User {public String getName(){return "LocalPreview";}}
    public void setScreenAndShow""")
    STUBS[mc] = STUBS[mc].replace("public Screen screen;", "public Screen screen;public Screen screen(){return screen;}")
    stub(P + ".network.AuthRequestPayload", """
public record AuthRequestPayload(Action action,String password,String confirmation) {
    public enum Action {LOGIN,REGISTER}
}
""")
    stub("net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking", """
public class ClientPlayNetworking {public static int sends;public static void send(Object packet){sends++;}}
""")
    stub(C + ".screen.PasswordResetScreen", """
public class PasswordResetScreen extends net.minecraft.client.gui.screens.Screen {
    public static int opened;
    public PasswordResetScreen(String name){super(net.minecraft.network.chat.Component.literal(""));opened++;}
}
""")


HARNESS = """
import java.lang.reflect.*;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;
import top.csituka.youzaiworldcore.client.screen.*;
import top.csituka.youzaiworldcore.client.screen.widget.*;
import top.csituka.youzaiworldcore.network.AuthRequestPayload;

public class AuthPreviewChecks {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static Object field(Object target,String name)throws Exception {
        Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);
    }
    static void invoke(Object target,String method)throws Exception {
        Method m=target.getClass().getDeclaredMethod(method);m.setAccessible(true);m.invoke(target);
    }
    static void fill(Screen screen,boolean register)throws Exception {
        ((EditBox)field(screen,"passwordField")).setValue("local-test-password");
        if(register)((EditBox)field(screen,"confirmPasswordField")).setValue("local-test-password");
    }
    static void preview(boolean register)throws Exception {
        Minecraft client=Minecraft.getInstance();
        Screen parent=new Screen(Component.literal("parent"));
        Screen screen=register?RegisterScreen.preview("LocalPreview",parent):LoginScreen.preview("LocalPreview",parent);
        client.gui.setScreen(screen);invoke(screen,"init");
        check(screen.shouldCloseOnEsc(),"预览必须允许关闭");
        String submit=register?"onRegisterClick":"onLoginClick";
        invoke(screen,submit);
        check(((ConfirmationDialog)field(screen,"currentDialog")).isVisible(),"本地校验反馈仍须可测试");
        fill(screen,register);
        // 模拟调整 GUI 尺寸，使用实际 init 验证预览与字段不会丢失。
        screen.width=854;screen.height=480;invoke(screen,"init");
        check(((EditBox)field(screen,"passwordField")).getValue().equals("local-test-password"),"重排丢失输入");
        check(screen.shouldCloseOnEsc(),"重排丢失预览模式");
        invoke(screen,submit);
        check(client.gui.screen()==screen,"提交预览不能跳入真实认证流程");
        check(((ConfirmationDialog)field(screen,"currentDialog")).isVisible(),"提交没有本地反馈");
        // 即使绕过按钮进入发送边界，也不能发出数据包。
        Method sender=screen.getClass().getDeclaredMethod("sendAuthRequest",AuthRequestPayload.class);
        sender.setAccessible(true);sender.invoke(screen,new AuthRequestPayload(
                register?AuthRequestPayload.Action.REGISTER:AuthRequestPayload.Action.LOGIN,"test","test"));
        if(!register) {
            invoke(screen,"onForgotPasswordClick");
            check(PasswordResetScreen.opened==0,"预览忘记密码不能打开真实找回流程");
        }
        var graphics=new GuiGraphicsExtractor();screen.extractRenderState(graphics,0,0,0);
        check(graphics.scissors==0,"预览裁剪栈未恢复");
        @SuppressWarnings("unchecked") var widgets=(List<AbstractWidget>)field(screen,"allWidgets");
        check(widgets.stream().filter(w->w instanceof MenuCloseButton).count()==1,"预览关闭按钮重复或丢失");
        var close=widgets.stream().filter(w->w instanceof MenuCloseButton).findFirst().orElseThrow();
        check(close.visible,"预览必须显示关闭按钮");
        screen.mouseClicked(new MouseButtonEvent(close.getX()+2,close.getY()+2,1),true);
        check(client.gui.screen()==parent,"预览 × 必须回到测试页");
        client.gui.setScreen(screen);GuiAnimationController.exiting=true;
        screen.keyPressed(new KeyEvent(41,false));
        check(client.gui.screen()==parent&&!GuiAnimationController.exiting,"Esc 必须越过弹窗和动画立即关闭");
        client.gui.setScreen(screen);invoke(screen,"onDisconnectClick");
        check(client.gui.screen()==parent,"预览的断开按钮应仅关闭预览");
        check(ClientPlayNetworking.sends==0&&client.disconnects==0,"预览触发网络副作用");
    }
    static void live(boolean register)throws Exception {
        Minecraft client=Minecraft.getInstance();
        Screen screen=register?new RegisterScreen("Live"):new LoginScreen("Live");
        client.gui.setScreen(screen);invoke(screen,"init");
        check(!screen.shouldCloseOnEsc(),"真实认证不得变成可关闭预览");
        screen.onClose();screen.keyPressed(new KeyEvent(41,false));
        check(client.gui.screen()==screen,"真实认证关闭限制丢失");
        @SuppressWarnings("unchecked") var widgets=(List<AbstractWidget>)field(screen,"allWidgets");
        check(widgets.stream().noneMatch(w->w instanceof MenuCloseButton),"真实认证不得显示关闭按钮");
        fill(screen,register);int before=ClientPlayNetworking.sends;
        invoke(screen,register?"onRegisterClick":"onLoginClick");
        check(ClientPlayNetworking.sends==before+1,"真实认证请求被预览逻辑拦截");
    }
    static void hub()throws Exception {
        Minecraft client=Minecraft.getInstance();Screen parent=new Screen(Component.literal("title"));
        int[] environment={0};var hub=new YouzaiWorldCoreTestScreen(parent,()->environment[0]++);
        client.player=null;client.gui.setScreen(hub);invoke(hub,"init");
        check(hub.registered.size()==4,"测试入口应包含两个预览、原测试环境与关闭");
        for(int i=0;i<2;i++) {
            var button=hub.registered.get(i);
            button.mouseClicked(new MouseButtonEvent(button.getX()+1,button.getY()+1,1),true);
            check(i==0?client.gui.screen() instanceof LoginScreen:client.gui.screen() instanceof RegisterScreen,"入口指向错误页面");
            check(client.gui.screen().shouldCloseOnEsc(),"测试入口必须创建预览实例");
            client.gui.screen().onClose();check(client.gui.screen()==hub,"预览关闭去向不正确");
        }
        check(environment[0]==0&&ClientPlayNetworking.sends==0,"预览入口不得自动进入测试环境或发包");
        var play=hub.registered.get(2);play.mouseClicked(new MouseButtonEvent(play.getX()+1,play.getY()+1,1),true);
        check(environment[0]==1,"原测试环境入口丢失");
        hub.keyPressed(new KeyEvent(41,false));check(client.gui.screen()==parent,"关闭测试页应返回标题");
    }
    public static void main(String[] args)throws Exception {
        // 保留非空的玩家及连接，确保“不发包”不是仅因没有服务器连接。
        preview(false);preview(true);hub();
        Minecraft.getInstance().player=new net.minecraft.client.player.LocalPlayer();
        live(false);live(true);
        System.out.println("账户预览回归通过："+checks+" 项断言；真实页面、本地副作用隔离、关闭与测试入口。");
    }
}
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home", default=os.environ.get("JAVA_HOME"))
    args = parser.parse_args()
    binary = Path(args.java_home) / "bin" if args.java_home else None
    suffix = ".exe" if os.name == "nt" else ""
    javac = str(binary / ("javac" + suffix)) if binary else "javac"
    java = str(binary / ("java" + suffix)) if binary else "java"
    adapt_stubs()
    production = [
        "screen/LoginScreen.java", "screen/RegisterScreen.java", "screen/YouzaiWorldCoreTestScreen.java",
        "screen/YzuiMenuScreen.java", "screen/widget/MenuCloseButton.java", "screen/widget/TransparentButton.java",
        "screen/widget/WidgetFocus.java", "render/YzuiMenuPanel.java", "render/YzuiPalette.java", "render/MenuIcon.java",
        "render/RoundedRect.java", "animation/YzuiHover.java", "animation/YzuiMotion.java", "config/YzuiVisualStyle.java",
    ]
    with tempfile.TemporaryDirectory(prefix="yz-auth-preview-", dir=ROOT) as temporary:
        temporary = Path(temporary)
        sources = []
        for relative, source in {**STUBS, "AuthPreviewChecks.java": HARNESS}.items():
            path = temporary / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source, encoding="utf-8")
            sources.append(str(path))
        subprocess.run([javac, "--release", "25", "-encoding", "UTF-8", "-d", str(temporary),
                        *sources, *[str(CLIENT / path) for path in production]], cwd=ROOT, check=True)
        subprocess.run([java, "-cp", str(temporary), "AuthPreviewChecks"], cwd=ROOT, check=True)
    for path in (ROOT / "src/main/resources/assets/youzaiworldcore/lang").glob("*.json"):
        pairs = json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=list)
        language = dict(pairs)
        assert len(pairs) == len(language), f"重复语言键：{path}"
        assert all("screen.youzaiworldcore.test." + suffix in language for suffix in (
            "login", "register", "environment", "description", "local_notice", "local_result")), path
    print("十种语言的预览文案检查通过。原版渲染与网络使用替身，未运行游戏或构建模组。")


if __name__ == "__main__":
    main()
