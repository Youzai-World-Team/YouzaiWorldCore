"""执行本地皮肤/披风预览的文件、模型状态、操作和生命周期回归。

真实预览代码运行在记录型 Minecraft/GPU 替身上；PNG 由 JDK ImageIO 解码。
另从当前原版 jar 核验所用接口。不启动游戏、不运行 Gradle、不访问网络。
"""

from pathlib import Path
import argparse
import json
import os
import subprocess
import tempfile
import zipfile
import verify_yzui_menu as fixtures
from verify_yzui_settings import ClassFile

ROOT, CLIENT, P, C = fixtures.ROOT, fixtures.CLIENT, fixtures.P, fixtures.C
STUBS, stub = fixtures.STUBS, fixtures.stub
PREFIX = "screen.youzaiworldcore.cosmetic_preview."


def check_resources_and_api():
    required = {PREFIX + key for key in (
        "title", "local_notice", "front", "back", "hide_cloak", "show_cloak", "reload", "folder", "rotate",
        "invalid", "skin_missing", "cloak_missing", "skin_conflict", "skin_wide", "skin_slim", "cloak_loaded")}
    locales = list((ROOT / "src/main/resources/assets/youzaiworldcore/lang").glob("*.json"))
    for path in locales:
        pairs = json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=list)
        values = dict(pairs)
        assert len(pairs) == len(values), f"重复语言键：{path}"
        assert {key for key in values if key.startswith(PREFIX)} == required, path
        assert all(isinstance(values[key], str) and values[key].strip() for key in required), path
        assert values[PREFIX + "invalid"].count("%s") == 1, path
    assert len(locales) == 10
    version = dict(line.split("=", 1) for line in (ROOT / "gradle.properties").read_text().splitlines()
                   if "=" in line and not line.startswith("#"))["minecraft_version"]
    jar = ROOT / "minecraft_jar" / version / (version + ".jar")
    with zipfile.ZipFile(jar) as archive:
        def load(name):
            return ClassFile(archive.read(name + ".class"))

        entity = "net/minecraft/client/renderer/entity/state/EntityRenderState"
        graphics = load("net/minecraft/client/gui/GuiGraphicsExtractor")
        descriptor = f"(L{entity};FLorg/joml/Vector3fc;Lorg/joml/Quaternionfc;Lorg/joml/Quaternionfc;IIII)V"
        assert graphics.methods.get(("entity", descriptor), 0) & 1, "GUI 实体绘制接口失配"
        blaze = load("com/mojang/blaze3d/Blaze3D")
        assert blaze.methods.get(("openPath", "(Ljava/nio/file/Path;)V"), 0) & 9 == 9, "目录打开接口失配"
        avatar = load("net/minecraft/client/renderer/entity/state/AvatarRenderState")
        assert avatar.methods.get(("<init>", "()V"), 0) & 1
        assert ("skin", "Lnet/minecraft/world/entity/player/PlayerSkin;") in avatar.fields
        assert ("showCape", "Z") in avatar.fields
        entity_types = load("net/minecraft/world/entity/EntityTypes")
        player_type = ("PLAYER", "Lnet/minecraft/world/entity/EntityType;")
        assert entity_types.fields.get(player_type, 0) & 9 == 9, "玩家类型常量必须来自 EntityTypes"
        assert player_type not in load("net/minecraft/world/entity/EntityType").fields, "旧常量位置不应进入测试替身"
        for owner, names, descriptor in (
                ("com/mojang/blaze3d/platform/InputConstants", ("KEY_LEFT", "KEY_RIGHT", "KEY_UP", "KEY_DOWN"), "I"),
                ("net/minecraft/world/entity/player/PlayerModelType", ("WIDE", "SLIM"),
                 "Lnet/minecraft/world/entity/player/PlayerModelType;"),
                ("net/minecraft/client/gui/narration/NarratedElementType", ("TITLE", "USAGE"),
                 "Lnet/minecraft/client/gui/narration/NarratedElementType;")):
            actual = load(owner)
            for name in names:
                assert actual.fields.get((name, descriptor), 0) & 9 == 9, f"预览常量失配：{owner}.{name}"
        image = load("com/mojang/blaze3d/platform/NativeImage")
        for signature in (("read", "([B)Lcom/mojang/blaze3d/platform/NativeImage;"),
                          ("copyRect", "(IIIIIIZZ)V"), ("getPixel", "(II)I"), ("setPixel", "(III)V")):
            assert image.methods.get(signature, 0) & 1, f"PNG 接口失配：{signature}"
    print(f"PASS {len(locales)} 种语言及 Minecraft {version} 预览/目录/PNG 接口")


def adapt_stubs():
    language = json.loads((ROOT / "src/main/resources/assets/youzaiworldcore/lang/en_us.json").read_text())
    labels = {key: value for key, value in language.items() if key.startswith(PREFIX)}
    labels.update({"gui.back": "Back", "screen.youzaiworldcore.account_management.button_close": "Close"})
    entries = ",".join("java.util.Map.entry(" + json.dumps(key) + "," + json.dumps(value, ensure_ascii=False) + ")"
                       for key, value in labels.items())
    stub("net.minecraft.network.chat.Component", """
public record Component(String value,String key) {
    private static final java.util.Map<String,String> TEXT=java.util.Map.ofEntries(""" + entries + """);
    public Component(String value){this(value,"");}
    public static Component empty(){return literal("");}
    public static Component literal(String value){return new Component(value);}
    public static Component translatable(String key,Object...args){
        String value=TEXT.getOrDefault(key,key);
        return new Component(args.length==0?value:String.format(value,args),key);
    }
    public String getString(){return value;}
    public Component copy(){return this;}
    public Component append(String other){return new Component(value+other,key);}
    public Component append(Component other){return append(other.getString());}
}
""")
    stub("net.minecraft.core.ClientAsset", """
public interface ClientAsset {net.minecraft.resources.Identifier id();public interface Texture extends ClientAsset {
    net.minecraft.resources.Identifier texturePath();
}}
""")
    stub("net.minecraft.world.entity.player.PlayerModelType", "public enum PlayerModelType {WIDE,SLIM}")
    stub("net.minecraft.world.entity.player.PlayerSkin", """
public record PlayerSkin(net.minecraft.core.ClientAsset.Texture body,net.minecraft.core.ClientAsset.Texture cape,
        net.minecraft.core.ClientAsset.Texture elytra,PlayerModelType model) {
    public static PlayerSkin insecure(net.minecraft.core.ClientAsset.Texture body,net.minecraft.core.ClientAsset.Texture cape,
            net.minecraft.core.ClientAsset.Texture elytra,PlayerModelType model){return new PlayerSkin(body,cape,elytra,model);}
}
""")
    stub("net.minecraft.client.resources.DefaultPlayerSkin", """
public class DefaultPlayerSkin {
    public static final net.minecraft.world.entity.player.PlayerSkin SKIN=net.minecraft.world.entity.player.PlayerSkin.insecure(
        new top.csituka.youzaiworldcore.client.cosmetic.CosmeticTexture(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft","default"),
            net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft","default")),null,null,
            net.minecraft.world.entity.player.PlayerModelType.WIDE);
    public static net.minecraft.world.entity.player.PlayerSkin get(java.util.UUID id){return SKIN;}
}
""")
    stub("net.minecraft.world.entity.EntityType", "public class EntityType {}")
    stub("net.minecraft.world.entity.EntityTypes", "public class EntityTypes {public static final EntityType PLAYER=new EntityType();}")
    stub("net.minecraft.client.renderer.entity.state.EntityRenderState", "public class EntityRenderState {}")
    stub("net.minecraft.client.renderer.entity.state.AvatarRenderState", """
public class AvatarRenderState extends EntityRenderState {
    public net.minecraft.world.entity.EntityType entityType;
    public net.minecraft.world.entity.player.PlayerSkin skin;
    public float boundingBoxWidth,boundingBoxHeight,eyeHeight,bodyRot;
    public boolean showHat=true,showJacket=true,showCape=true;
}
""")
    stub("org.joml.Quaternionf", """
public class Quaternionf {public float x,z;public Quaternionf rotateX(float a){x+=a;return this;}
    public Quaternionf rotateZ(float a){z+=a;return this;}public Quaternionf mul(Quaternionf q){x+=q.x;z+=q.z;return this;}}
""")
    stub("org.joml.Vector3f", "public record Vector3f(float x,float y,float z) {}")
    stub("net.minecraft.client.sounds.SoundManager", "public class SoundManager {}")
    stub("net.minecraft.client.gui.narration.NarratedElementType", "public enum NarratedElementType {TITLE,USAGE}")
    stub("net.minecraft.client.gui.narration.NarrationElementOutput", """
public interface NarrationElementOutput {
    void add(NarratedElementType type,net.minecraft.network.chat.Component message);
}
""")
    widget = "net/minecraft/client/gui/components/AbstractWidget.java"
    STUBS[widget] = STUBS[widget].replace("public void playDownSound(Object o){}", """
    public void playDownSound(net.minecraft.client.sounds.SoundManager o){}
    protected void onDrag(MouseButtonEvent event,double dx,double dy){}
    public boolean mouseDragged(MouseButtonEvent event,double dx,double dy){onDrag(event,dx,dy);return true;}
""")
    stub("net.minecraft.client.gui.screens.Screen", """
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
public class Screen {
    public int width=854,height=480;
    protected final Font font=new Font();
    private final Component title;
    private AbstractWidget focused;
    public final java.util.List<AbstractWidget> registered=new java.util.ArrayList<>();
    public Screen(Component title){this.title=title;}
    public Component getTitle(){return title;}
    public void init(int w,int h){width=w;height=h;registered.clear();init();}
    protected void init(){}
    protected <T extends AbstractWidget> T addRenderableWidget(T w){registered.add(w);return w;}
    public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float tick){
        for(var widget:registered)widget.extractRenderState(g,x,y,tick);
    }
    public void onClose(){}
    public void removed(){}
    public boolean shouldCloseOnEsc(){return true;}
    public boolean isPauseScreen(){return false;}
    public boolean keyPressed(KeyEvent event){
        if(event.key()==41&&shouldCloseOnEsc()){onClose();return true;}
        return focused!=null&&focused.keyPressed(event);
    }
    public boolean mouseClicked(MouseButtonEvent event,boolean actual){
        for(var widget:registered)if(widget.mouseClicked(event,actual)){
            if(focused!=null)focused.setFocused(false);focused=widget;widget.setFocused(true);return true;
        }return false;
    }
}
""")
    graphics = "net/minecraft/client/gui/GuiGraphicsExtractor.java"
    STUBS[graphics] = STUBS[graphics].replace("public int scissors;", """
    public record EntityPicture(net.minecraft.client.renderer.entity.state.AvatarRenderState state,float scale,int x,int y,int r,int b){}
    public final List<EntityPicture> entities=new ArrayList<>();
    public boolean failEntity;
    public int scissors;
    public void entity(net.minecraft.client.renderer.entity.state.AvatarRenderState state,float scale,
            org.joml.Vector3f translation,org.joml.Quaternionf rotation,org.joml.Quaternionf camera,int x,int y,int r,int b){
        if(failEntity)throw new IllegalStateException("GPU test failure");
        entities.add(new EntityPicture(state,scale,x,y,r,b));box("model",x,y,r-x,b-y,"");
    }
    public void setTooltipForNextFrame(Font font,Component message,int x,int y){}
""")
    mc = "net/minecraft/client/Minecraft.java"
    STUBS[mc] = STUBS[mc].replace("public Textures getTextureManager(){return new Textures();}", """
    public final Textures textures=new Textures();
    public Textures getTextureManager(){return textures;}
    public User getUser(){return new User();}
    public static class User {public java.util.UUID getProfileId(){return new java.util.UUID(0,1);}}
""").replace("public Object getSoundManager(){return this;}",
             "public net.minecraft.client.sounds.SoundManager getSoundManager(){return new net.minecraft.client.sounds.SoundManager();}") \
        .replace("public static class Gui {public Screen screen;public void setScreen(Screen s){screen=s;}}", """
    public static class Gui {
        public Screen screen;public Screen screen(){return screen;}
        public void setScreen(Screen s){if(screen!=null&&screen!=s)screen.removed();screen=s;if(s!=null)s.init(854,480);}
    }
""").replace("public static class Textures {public Texture getTexture(Identifier id){return new Texture();}}", """
    public static class Textures {
        public final java.util.Map<Identifier,net.minecraft.client.renderer.texture.DynamicTexture> live=new java.util.LinkedHashMap<>();
        public int registers,releases;public boolean failRegistration;
        public Texture getTexture(Identifier id){return new Texture();}
        public void register(Identifier id,net.minecraft.client.renderer.texture.DynamicTexture value){
            if(failRegistration)throw new IllegalStateException("registration failure");
            var old=live.put(id,value);if(old!=null)old.close();registers++;
        }
        public void release(Identifier id){var texture=live.remove(id);if(texture!=null){texture.close();releases++;}}
    }
""")
    player = "net/minecraft/client/player/LocalPlayer.java"
    STUBS[player] = STUBS[player].replace("public final Connection connection", """
    public net.minecraft.world.entity.player.PlayerSkin getSkin(){return net.minecraft.client.resources.DefaultPlayerSkin.SKIN;}
    public final Connection connection""")
    theme = C.replace(".", "/") + "/render/YzuiTheme.java"
    STUBS[theme] = STUBS[theme].replace("public static void card(GuiGraphicsExtractor g,int x,int y,int w,int h,float a)", """
    public static void card(GuiGraphicsExtractor g,int x,int y,int w,int h){card(g,x,y,w,h,1f);}
    public static void card(GuiGraphicsExtractor g,int x,int y,int w,int h,float a)""")
    stub(C + ".render.YzuiMenuPanel", """
public class YzuiMenuPanel {public static void card(net.minecraft.client.gui.GuiGraphicsExtractor g,int x,int y,int w,int h){
    top.csituka.youzaiworldcore.client.render.YzuiTheme.card(g,x,y,w,h);}}
""")
    stub(P + ".util.DebugLogger", """
public class DebugLogger {public static void debug(String m,String s,Object...args){}public static void info(String m,String s,Object...args){}
    public static void warn(String m,String s,Object...args){}public static void exception(String m,String s,Throwable e){}}
""")
    stub(C + ".config.ClientGlobalSettings", "public class ClientGlobalSettings {public static final String COSMETIC_MODULE=\"cosmetic_module\";}")
    stub(P + ".config.ModPaths", """
public class ModPaths {
    public static java.nio.file.Path clientConfig(String module){return java.nio.file.Path.of(System.getProperty("preview.data")).resolve(module);}
    public static void ensureDir(java.nio.file.Path path){try{java.nio.file.Files.createDirectories(path);}catch(java.io.IOException e){throw new RuntimeException(e);}}
}
""")
    stub("com.mojang.blaze3d.Blaze3D", """
public class Blaze3D {public static java.nio.file.Path opened;public static void openPath(java.nio.file.Path path){opened=path;}}
""")
    stub("net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking", """
public class ClientPlayNetworking {public static int sends;public static void send(Object packet){sends++;}}
""")
    stub("net.minecraft.client.renderer.texture.DynamicTexture", """
public class DynamicTexture implements AutoCloseable {
    public final com.mojang.blaze3d.platform.NativeImage image;
    public DynamicTexture(java.util.function.Supplier<String> name,com.mojang.blaze3d.platform.NativeImage image){this.image=image;}
    public void close(){image.close();}
}
""")
    stub("com.mojang.blaze3d.platform.NativeImage", """
public class NativeImage implements AutoCloseable {
    public static int created,closed,reads;
    private final int width,height;private final int[] pixels;private boolean released;
    public NativeImage(int width,int height,boolean clear){this.width=width;this.height=height;pixels=new int[width*height];created++;}
    public static NativeImage read(byte[] bytes)throws java.io.IOException{
        reads++;var decoded=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        if(decoded==null)throw new java.io.IOException("invalid PNG");
        var image=new NativeImage(decoded.getWidth(),decoded.getHeight(),true);
        decoded.getRGB(0,0,image.width,image.height,image.pixels,0,image.width);return image;
    }
    public int getWidth(){return width;}public int getHeight(){return height;}
    public int getPixel(int x,int y){if(released)throw new AssertionError("读取已释放图像");return pixels[y*width+x];}
    public void setPixel(int x,int y,int color){if(released)throw new AssertionError("写入已释放图像");pixels[y*width+x]=color;}
    public void copyFrom(NativeImage source){for(int y=0;y<Math.min(height,source.height);y++)for(int x=0;x<Math.min(width,source.width);x++)setPixel(x,y,source.getPixel(x,y));}
    public void fillRect(int x,int y,int w,int h,int color){for(int a=0;a<w;a++)for(int b=0;b<h;b++)setPixel(x+a,y+b,color);}
    public void copyRect(int x,int y,int dx,int dy,int w,int h,boolean flipX,boolean flipY){
        int[] copy=pixels.clone();for(int a=0;a<w;a++)for(int b=0;b<h;b++)
            setPixel(x+dx+(flipX?w-1-a:a),y+dy+(flipY?h-1-b:b),copy[(y+b)*width+x+a]);
    }
    public void close(){if(!released){released=true;closed++;}}
}
""")


HARNESS = r"""
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerModelType;
import top.csituka.youzaiworldcore.client.cosmetic.*;
import top.csituka.youzaiworldcore.client.screen.*;
import top.csituka.youzaiworldcore.client.screen.widget.*;

public class CosmeticPreviewChecks {
    static int checks;
    static final Minecraft client=Minecraft.getInstance();
    static final Path directory=CosmeticPreviewTextures.directory();
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static void png(String name,int w,int h,int seed)throws Exception {
        Files.createDirectories(directory);var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        for(int x=0;x<w;x++)for(int y=0;y<h;y++)image.setRGB(x,y,0xFF000000|((seed+x*17+y*31)&0xFFFFFF));
        ImageIO.write(image,"png",directory.resolve(name).toFile());
    }
    static void clearFiles()throws Exception {
        for(String name:List.of("skin.png","skin_slim.png","cloak.png"))Files.deleteIfExists(directory.resolve(name));
    }
    static void files()throws Exception {
        Files.createDirectories(directory);Files.writeString(directory.resolve("upload_state.json"),"unchanged-upload-state");
        var preview=new CosmeticPreviewTextures(DefaultPlayerSkin.SKIN);
        check(preview.skinFile().status()==CosmeticPreviewTextures.Status.MISSING,"无文件时的皮肤状态");
        check(!preview.hasCloak()&&preview.skin().cape()==null,"无文件不能显示真实玩家披风");
        check(preview.skin().body().equals(DefaultPlayerSkin.SKIN.body()),"无皮肤时回落默认外观");
        check(client.textures.live.isEmpty(),"空预览不得分配纹理");
        png("skin_slim.png",64,64,1);png("cloak.png",64,32,2);preview.reload(DefaultPlayerSkin.SKIN);
        check(preview.skin().model()==PlayerModelType.SLIM&&preview.hasCloak(),"细模型与披风组合");
        check(client.textures.live.size()==2,"仅分配皮肤与披风纹理");
        check(client.textures.live.get(preview.skin().cape().texturePath()).image.getHeight()==32,"披风尺寸不转换");
        png("skin.png",64,32,3);preview.reload(DefaultPlayerSkin.SKIN);
        check(preview.skinConflict()&&preview.skin().model()==PlayerModelType.WIDE,"宽模型冲突优先级");
        check(client.textures.live.get(preview.skin().body().texturePath()).image.getHeight()==64,"旧皮肤展开为现代布局");
        check(client.textures.live.size()==2,"重新读取释放旧纹理");
        int reads=NativeImage.reads;
        Files.write(directory.resolve("skin.png"),new byte[524289]);preview.reload(DefaultPlayerSkin.SKIN);
        check(preview.skinFile().status()==CosmeticPreviewTextures.Status.INVALID&&preview.hasCloak(),"超大皮肤不影响披风");
        check(preview.skin().body().equals(DefaultPlayerSkin.SKIN.body()),"非法宽皮肤不得静默改用细模型");
        check(NativeImage.reads==reads+1,"超大文件必须在解码前拒绝");
        png("skin.png",32,32,4);preview.reload(DefaultPlayerSkin.SKIN);
        check(preview.skinFile().status()==CosmeticPreviewTextures.Status.INVALID,"非法图片尺寸");
        byte[] broken=new byte[24];byte[] magic={(byte)137,80,78,71,13,10,26,10};System.arraycopy(magic,0,broken,0,8);
        broken[12]='I';broken[13]='H';broken[14]='D';broken[15]='R';broken[19]=64;broken[23]=64;
        Files.write(directory.resolve("skin.png"),broken);preview.reload(DefaultPlayerSkin.SKIN);
        check(preview.skinFile().status()==CosmeticPreviewTextures.Status.INVALID&&preview.hasCloak(),"PNG 头有效但内容损坏");
        check(client.textures.live.size()==1,"损坏皮肤不泄漏纹理");
        png("skin.png",64,64,5);client.textures.failRegistration=true;preview.reload(DefaultPlayerSkin.SKIN);
        check(preview.skinFile().status()==CosmeticPreviewTextures.Status.INVALID&&!preview.hasCloak(),"纹理注册失败应独立报告");
        check(client.textures.live.isEmpty()&&NativeImage.created==NativeImage.closed,"注册失败释放已解码图像");
        client.textures.failRegistration=false;preview.close();preview.close();clearFiles();
        check(Files.readString(directory.resolve("upload_state.json")).equals("unchanged-upload-state"),"预览不得修改上传确认记录");
    }
    static void legacy() {
        var old=new NativeImage(64,32,true);
        for(int x=0;x<64;x++)for(int y=0;y<32;y++)old.setPixel(x,y,0xFF000000|(x<<8)|y);
        int original=old.getPixel(4,20),closed=NativeImage.closed;
        var image=CosmeticSkinImages.prepare(old);
        check(image.getHeight()==64&&NativeImage.closed==closed+1,"展开后释放原始 64×32 图像");
        check(image.getPixel(23,52)==original,"镜像补齐左腿布局");
        check((image.getPixel(32,0)>>>24)==0,"旧版不透明帽子区按原版去除透明度");
        check((image.getPixel(0,20)>>>24)==255&&(image.getPixel(16,48)>>>24)==255,"基础层保持不透明");
        check(image.getPixel(0,40)==0,"新增外层保持透明");image.close();
        var modern=new NativeImage(64,64,true);modern.setPixel(40,8,0x80445566);
        check(CosmeticSkinImages.prepare(modern)==modern&&modern.getPixel(40,8)==0x80445566,"现代皮肤保留外层透明度");
        check(modern.getPixel(0,0)==0,"现代自定义 PNG 保留原有透明区域");modern.close();
    }
    static AbstractWidget button(Screen screen,String suffix) {
        return screen.registered.stream().filter(w->w.getMessage().key().equals("screen.youzaiworldcore.cosmetic_preview."+suffix)).findFirst().orElseThrow();
    }
    static void click(Screen screen,AbstractWidget widget) {
        check(screen.mouseClicked(new MouseButtonEvent(widget.getX()+1,widget.getY()+1,1),true),"按钮必须可点击");
    }
    static GuiGraphicsExtractor render(Screen screen) {
        var g=new GuiGraphicsExtractor();screen.extractRenderState(g,0,0,0);
        check(g.scissors==0&&g.entities.size()==1,"模型提交且裁剪栈平衡");return g;
    }
    static void page()throws Exception {
        png("skin_slim.png",64,64,6);png("cloak.png",64,32,7);
        client.player=null;client.level=null;client.connection=null;
        Screen parent=new Screen(Component.literal("settings"));var page=new CosmeticPreviewScreen(parent);
        client.gui.setScreen(page);
        check(page.registered.size()==8,"模型、关闭和六个操作入口");
        check(!page.isPauseScreen(),"预览不改变暂停语义");
        var first=render(page);var state=first.entities.getFirst().state();
        check(state.skin.model()==PlayerModelType.SLIM&&state.skin.cape()!=null,"无世界和无连接仍可预览");
        check(state.entityType==net.minecraft.world.entity.EntityTypes.PLAYER,"预览使用 26.3 的真实玩家类型常量位置");
        check(state.bodyRot==180&&state.showHat&&state.showJacket&&state.showCape,"完整正面模型与外层");
        click(page,button(page,"back"));check(render(page).entities.getFirst().state().bodyRot==0,"背面入口");
        click(page,button(page,"front"));check(render(page).entities.getFirst().state().bodyRot==180,"正面入口");
        click(page,button(page,"hide_cloak"));check(render(page).entities.getFirst().state().skin.cape()==null,"临时隐藏披风");
        check(client.textures.live.size()==2,"隐藏披风不删除文件或纹理");
        click(page,button(page,"show_cloak"));
        var model=(CosmeticPreviewWidget)page.registered.getFirst();
        model.mouseDragged(new MouseButtonEvent(model.getX()+1,model.getY()+1,1),8,50);
        check(render(page).entities.getFirst().state().bodyRot==200,"拖动旋转");
        model.keyPressed(new KeyEvent(80,false));check(render(page).entities.getFirst().state().bodyRot==185,"方向键旋转");
        int registered=client.textures.registers;
        for(int[] size:new int[][]{{320,240},{480,270},{640,360},{854,480},{1920,1080}}) {
            page.init(size[0],size[1]);var g=render(page);
            check(page.registered.getFirst()==model&&g.entities.getFirst().state().bodyRot==185,"重排保留模型与方向");
            check(client.textures.registers==registered,"重排不重复读取或注册 PNG");
            for(var widget:page.registered)check(widget.getX()>=0&&widget.getY()>=0&&widget.getRight()<=size[0]
                    &&widget.getBottom()<=size[1]&&widget.getWidth()>0&&widget.getHeight()>0,"控件应位于窗口内");
            check(g.entities.getFirst().scale()>0,"模型缩放为正");
        }
        var failing=new GuiGraphicsExtractor();failing.failEntity=true;
        try {model.extractRenderState(failing,0,0,0);throw new AssertionError("未触发绘制异常");}
        catch(IllegalStateException expected){check(failing.scissors==0,"绘制失败仍恢复裁剪栈");}
        var oldId=render(page).entities.getFirst().state().skin.body().texturePath();
        png("skin_slim.png",64,64,8);click(page,button(page,"reload"));
        check(!render(page).entities.getFirst().state().skin.body().texturePath().equals(oldId)&&client.textures.live.size()==2,"重新读取替换快照并释放旧资源");
        Files.delete(directory.resolve("cloak.png"));click(page,button(page,"reload"));
        check(!button(page,"hide_cloak").active&&render(page).entities.getFirst().state().skin.cape()==null,"删除披风后刷新禁用切换");
        click(page,button(page,"folder"));check(com.mojang.blaze3d.Blaze3D.opened.equals(directory),"目录按钮指向实际外观目录");
        click(page,page.registered.stream().filter(w->w instanceof MenuCloseButton).findFirst().orElseThrow());
        check(client.gui.screen()==parent&&client.textures.live.isEmpty(),"关闭回到父页面并释放纹理");
        client.player=new net.minecraft.client.player.LocalPlayer();client.level=new Minecraft.Level();
        page=new CosmeticPreviewScreen(parent);client.gui.setScreen(page);page.keyPressed(new KeyEvent(41,false));
        check(client.gui.screen()==parent&&client.textures.live.isEmpty(),"游戏内 Esc 返回并释放纹理");
        client.level=null;page=new CosmeticPreviewScreen(parent);client.gui.setScreen(page);page.keyPressed(new KeyEvent(41,false));
        check(client.gui.screen()==parent&&client.textures.live.isEmpty(),"标题页 Esc 返回并释放纹理");
        check(NativeImage.created==NativeImage.closed,"全部预览图像最终释放");
        check(net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.sends==0,"预览操作不得发包");
        check(Files.readString(directory.resolve("upload_state.json")).equals("unchanged-upload-state"),"所有预览操作保持上传记录");
    }
    public static void main(String[] args)throws Exception {
        files();legacy();page();
        System.out.println("PASS 本地皮肤/披风预览："+checks+" 项断言；PNG、旧皮肤、旋转、重排、重读、关闭、资源释放与无网络副作用。");
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
    check_resources_and_api()
    adapt_stubs()
    production = [CLIENT / name for name in (
        "cosmetic/CosmeticSkinImages.java", "cosmetic/CosmeticPreviewTextures.java", "cosmetic/CosmeticTexture.java",
        "screen/CosmeticPreviewScreen.java", "screen/widget/CosmeticPreviewWidget.java", "screen/YzuiMenuScreen.java",
        "screen/widget/TransparentButton.java", "screen/widget/MenuCloseButton.java", "render/MenuIcon.java",
        "render/YzuiPalette.java", "render/RoundedRect.java", "animation/YzuiHover.java", "animation/YzuiMotion.java",
        "config/YzuiVisualStyle.java")]
    production.append(ROOT / "src/main/java/top/csituka/youzaiworldcore/cosmetic/CosmeticPngValidator.java")
    with tempfile.TemporaryDirectory(prefix="yz-cosmetic-preview-") as temporary:
        temporary = Path(temporary)
        sources = []
        for relative, source in {**STUBS, "CosmeticPreviewChecks.java": HARNESS}.items():
            path = temporary / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source, encoding="utf-8")
            sources.append(str(path))
        subprocess.run([javac, "--release", "25", "-encoding", "UTF-8", "-d", str(temporary),
                        *sources, *map(str, production)], cwd=ROOT, check=True)
        subprocess.run([java, "-Djava.awt.headless=true", "-Dpreview.data=" + str(temporary / "data"),
                        "-cp", str(temporary), "CosmeticPreviewChecks"], cwd=ROOT, check=True)
    print("原版渲染与平台打开使用替身；未启动游戏或构建模组。")


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as error:
        raise SystemExit(error.returncode) from None
