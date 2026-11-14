"""执行实际标题布局、滚动状态和无字体加载绘制代码；不启动 Minecraft。

可选 --preview-dir 输出加载卡片的实际几何绘制记录，供人工检查（不代表 GPU 实机验证）。
"""
from pathlib import Path
import argparse
import json
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / 'src/client/java/top/csituka/youzaiworldcore/client'
STUBS = {
    'net/minecraft/resources/Identifier.java': '''package net.minecraft.resources;
public record Identifier(String path) {
    public static Identifier fromNamespaceAndPath(String namespace,String path) { return new Identifier(namespace+":"+path); }
}
''',
    'net/minecraft/client/renderer/RenderPipelines.java': '''package net.minecraft.client.renderer;
public final class RenderPipelines { public static final Object GUI_TEXTURED = new Object(); }
''',
    'top/csituka/youzaiworldcore/client/render/YzuiTheme.java': '''package top.csituka.youzaiworldcore.client.render;
public final class YzuiTheme {
    public static YzuiPalette current = YzuiPalette.LIGHT;
    public static YzuiPalette palette() { return current; }
    public static int alpha(int color, float alpha) { return (Math.round(Math.clamp(alpha,0f,1f)*255)<<24)|(color&0xFFFFFF); }
}
''',
    'net/minecraft/client/gui/GuiGraphicsExtractor.java': '''package net.minecraft.client.gui;
import java.util.*;
import net.minecraft.resources.Identifier;
public final class GuiGraphicsExtractor {
    public final List<int[]> fills = new ArrayList<>();
    public final List<int[]> blits = new ArrayList<>();
    private final int width,height;
    public GuiGraphicsExtractor(int w,int h) { width=w; height=h; }
    public int guiWidth() { return width; }
    public int guiHeight() { return height; }
    public void fill(int x1,int y1,int x2,int y2,int color) {
        if(x1<0||y1<0||x2>width||y2>height||x2<x1||y2<y1) throw new AssertionError("Fill outside screen");
        fills.add(new int[]{x1,y1,x2,y2,color});
    }
    public void blit(Object pipe,Identifier texture,int x,int y,float u,float v,int w,int h,int tw,int th,int tint) {
        blit(pipe,texture,x,y,u,v,w,h,w,h,tw,th,tint);
    }
    public void blit(Object pipe,Identifier texture,int x,int y,float u,float v,int w,int h,int sw,int sh,int tw,int th,int tint) {
        if(x<0||y<0||x+w>width||y+h>height||u<0||v<0||u+sw>tw||v+sh>th) throw new AssertionError("Invalid logo UV or bounds");
        blits.add(new int[]{x,y,w,h,(int)u,(int)v,sw,sh,tw,th,tint});
    }
}
''',
}

HARNESS = '''import java.nio.file.*;
import java.util.*;
import top.csituka.youzaiworldcore.client.screen.title.TitleMenuLayout;
import top.csituka.youzaiworldcore.client.screen.title.TitleMenuLayout.Rect;
import top.csituka.youzaiworldcore.client.render.*;
import top.csituka.youzaiworldcore.update.TitleScreenScrollState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
public class TitleLoadingChecks {
    static int assertions,layouts,frames;
    static void check(boolean condition,String message) { assertions++; if(!condition) throw new AssertionError(message); }
    static boolean inside(Rect inner,Rect outer) { return inner.x()>=outer.x()&&inner.y()>=outer.y()&&inner.right()<=outer.right()&&inner.bottom()<=outer.bottom(); }
    static boolean overlaps(Rect a,Rect b) { return a.x()<b.right()&&b.x()<a.right()&&a.y()<b.bottom()&&b.y()<a.bottom(); }
    static void layouts() {
        for(int w:new int[]{320,360,427,479,480,640,854,960,1280})
        for(int h:new int[]{240,270,299,300,360,480,720}) for(boolean developer:new boolean[]{false,true}) {
            var l=TitleMenuLayout.of(w,h,developer);layouts++;
            Rect screen=new Rect(0,0,w,h);
            for(Rect r:List.of(l.logo(),l.subtitle(),l.theme(),l.navigation(),l.news())) check(inside(r,screen),"Element outside screen");
            check(Math.abs(l.logo().height()-l.logo().width()*1024.0/4383)<1,"Title logo stretched");
            check(!overlaps(l.logo(),l.theme())&&!overlaps(l.navigation(),l.news()),"Cards/header overlap");
            check(l.subtitle().bottom()<=l.navigation().y(),"Subtitle overlaps cards");
            var actions=new ArrayList<>(List.of(l.join(),l.options(),l.quit()));
            if(developer) actions.add(l.test());
            for(int i=0;i<actions.size();i++) {
                Rect a=actions.get(i); check(inside(a,l.navigation()),"Action clipped");
                check(a.width()>=100&&a.height()>=20,"Action too small");
                check(a.y()>=l.navigation().y()+l.padding()+28,"Action overlaps section label");
                if(i>0) check(!overlaps(actions.get(i-1),a),"Actions overlap");
            }
            for(boolean update:new boolean[]{false,true}) for(boolean forced:new boolean[]{false,true}) {
                Rect body=l.body(update,forced);
                check(inside(body,l.news())&&body.width()>100&&body.height()>=24,"News unreadable");
                if(update) {
                    check(inside(l.download(forced),l.news()),"Download clipped");
                    check(body.bottom()<l.download(forced).y(),"Body overlaps download");
                    if(!forced) check(inside(l.ignore(),l.news())&&!overlaps(l.download(false),l.ignore()),"Ignore clipped");
                }
            }
        }
    }
    static void scrolling() {
        TitleScreenScrollState.reset();
        check(!TitleScreenScrollState.contains(0,0),"Empty viewport accepts input");
        TitleScreenScrollState.setViewport(20,30,120,50,200);
        TitleScreenScrollState.setScrollOffset(999);
        check(TitleScreenScrollState.getScrollOffset()==150,"Cannot reach end of content");
        check(TitleScreenScrollState.contains(139,79)&&!TitleScreenScrollState.contains(140,79)
                &&!TitleScreenScrollState.contains(139,80)&&!TitleScreenScrollState.contains(19,30),"Hit region mismatch");
        TitleScreenScrollState.setViewport(20,30,120,150,200);
        check(TitleScreenScrollState.getScrollOffset()==50,"Resize leaves stale scroll");
        TitleScreenScrollState.setViewport(20,30,120,150,30);
        check(TitleScreenScrollState.getScrollOffset()==0&&TitleScreenScrollState.getMaxScroll()==0,"Short announcement keeps old scroll");
        for(double d:new double[]{-5,Double.NaN,Double.POSITIVE_INFINITY}) {
            TitleScreenScrollState.setScrollOffset(d);check(TitleScreenScrollState.getScrollOffset()==0,"Invalid input not clamped");
        }
    }
    static void drawing(String preview)throws Exception {
        for(var palette:List.of(YzuiPalette.LIGHT,YzuiPalette.DARK)) {
            YzuiTheme.current=palette;
            check(YzuiPalette.contrast(palette.textMuted(),palette.surface())>=4.5,"Loading percentage contrast");
            check(YzuiPalette.contrast(palette.primary(),palette.surfaceHigh())>=3,"Loading progress contrast");
            int behind=palette==YzuiPalette.DARK?0xFFFFFF:0;
            for(int fg:new int[]{palette.textMuted(),palette.primary()}) check(YzuiPalette.contrast(
                    palette.translucentText(fg,0.72f),YzuiPalette.composite(palette.background(),behind,0.72f))>=4.5,"Header contrast");
            for(int[] size:new int[][]{{320,240},{427,240},{480,300},{854,480},{1280,720}})
            for(float progress:new float[]{-1,0,0.01f,0.37f,0.999f,1,2,Float.NaN})
            for(float alpha:new float[]{0,0.25f,0.5f,1}) {
                var g=new GuiGraphicsExtractor(size[0],size[1]);
                YzuiLoadingRenderer.render(g,new Identifier("earlyLogo"),progress,alpha);frames++;
                if(alpha==0) {check(g.fills.isEmpty()&&g.blits.isEmpty(),"Transparent overlay still draws");continue;}
                check(g.blits.size()==1,"Logo should be drawn as one image");
                for(int[] f:g.fills) check((f[4]>>>24)<=Math.round(255*alpha),"Fade ignored");
                var layout=YzuiLoadingRenderer.Layout.of(size[0],size[1]);
                for(int[] b:g.blits) {
                    check((b[10]>>>24)==Math.round(255*alpha),"Logo opacity mismatch");
                    check((b[10]&0xFFFFFF)==0xFFFFFF,"Logo original colors changed");
                    check(Math.abs(b[3]-b[2]*1024.0/4383)<1,"Loading logo stretched");
                    check(b[1]+b[3]<layout.barY(),"Logo overlaps progress bar");
                }
                int right=layout.barX();
                for(int[] f:g.fills) if(f[1]>=layout.barY()&&f[3]<=layout.barY()+6&&(f[4]&0xFFFFFF)==(palette.primary()&0xFFFFFF)) right=Math.max(right,f[2]);
                check(right<=layout.barX()+layout.barWidth(),"Progress exceeds track");
                if(progress==0||!Float.isFinite(progress)) check(right==layout.barX(),"Zero progress paints fill");
                if(progress==1) check(right==layout.barX()+layout.barWidth(),"Completed progress does not fill track");
            }
            if(!preview.isEmpty()) {
                var g=new GuiGraphicsExtractor(854,480);
                YzuiLoadingRenderer.background(g,1f);
                YzuiLoadingRenderer.render(g,new Identifier("earlyLogo"),0.67f,1f);
                List<String> lines=new ArrayList<>();
                for(int[] f:g.fills) lines.add("F "+Arrays.toString(f));
                for(int[] b:g.blits) lines.add("B "+Arrays.toString(b));
                Files.write(Path.of(preview,palette==YzuiPalette.DARK?"loading-dark.txt":"loading-light.txt"),lines);
            }
        }
    }
    static void logoResource()throws Exception {
        // 在未初始化 Minecraft 和资源管理器的环境下读取真实的启动图片。
        try(var stream=YzuiBrandLogo.openStream()) {
            var image=javax.imageio.ImageIO.read(stream);
            check(image!=null&&image.getWidth()==YzuiBrandLogo.TEXTURE_WIDTH
                    &&image.getHeight()==YzuiBrandLogo.TEXTURE_HEIGHT,"Logo resource dimensions changed");
            int left=image.getWidth(),top=image.getHeight(),right=0,bottom=0;
            for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) if((image.getRGB(x,y)>>>24)!=0) {
                left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x+1);bottom=Math.max(bottom,y+1);
            }
            check(left==YzuiBrandLogo.CONTENT_X&&top==YzuiBrandLogo.CONTENT_Y
                    &&right-left==YzuiBrandLogo.CONTENT_WIDTH&&bottom-top==YzuiBrandLogo.CONTENT_HEIGHT,"Logo crop cuts off artwork");
        }
    }
    public static void main(String[] args)throws Exception {
        logoResource();layouts();scrolling();drawing(args.length==0?"":args[0]);
        System.out.println("Passed: "+assertions+" assertions, "+layouts+" title layouts, "+frames+" loading frames.");
    }
}
'''


def check_resources():
    languages = ROOT / 'src/main/resources/assets/youzaiworldcore/lang'
    keys = ['subtitle', 'play', 'theme_light', 'theme_dark']
    for path in languages.glob('*.json'):
        def unique(pairs):
            result = dict(pairs)
            assert len(pairs) == len(result), f'Duplicate language key: {path.name}'
            return result
        data = json.loads(path.read_text(encoding='utf-8-sig'), object_pairs_hook=unique)
        for key in keys:
            assert data['title.youzaiworldcore.' + key].strip()
            assert '??' not in data['title.youzaiworldcore.' + key], path.name
    print('Title text present in all 10 language files; JSON and encoding checks passed.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', required=True)
    parser.add_argument('--preview-dir')
    args = parser.parse_args()
    suffix = '.exe' if os.name == 'nt' else ''
    binary = Path(args.java_home) / 'bin'
    sources = [CLIENT / p for p in ['render/RoundedRect.java', 'render/YzuiPalette.java',
                                    'render/YzuiBrandLogo.java', 'render/YzuiLoadingRenderer.java', 'screen/title/TitleMenuLayout.java']]
    sources.append(ROOT / 'src/main/java/top/csituka/youzaiworldcore/update/TitleScreenScrollState.java')
    preview = Path(args.preview_dir).resolve() if args.preview_dir else None
    if preview:
        preview.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='.tmp-yzui-title-', dir=ROOT) as directory:
        temporary = Path(directory)
        for name, content in {**STUBS, 'TitleLoadingChecks.java': HARNESS}.items():
            path = temporary / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding='utf-8')
            sources.append(path)
        subprocess.run([str(binary / ('javac' + suffix)), '--release', '25', '-encoding', 'UTF-8',
                        '-d', str(temporary), *map(str, sources)], cwd=ROOT, check=True)
        classpath = os.pathsep.join([str(temporary), str(ROOT / 'src/client/resources')])
        subprocess.run([str(binary / ('java' + suffix)), '-Djava.awt.headless=true', '-cp', classpath, 'TitleLoadingChecks',
                        *([str(preview)] if preview else [])], cwd=ROOT, check=True)
    check_resources()


if __name__ == '__main__':
    main()
