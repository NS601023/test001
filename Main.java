// 使用例及び解説：https://ns601023.hatenablog.com/entry/2021/02/17/184312
import java.util.*;
import java.io.*;
import java.awt.*;
import java.awt.geom.Point2D;
import static java.lang.Math.*;
import java.awt.image.*;
import javax.imageio.ImageIO;
public class Main {
    public static void main(String[] args) throws IOException {
        Random rand = new Random();
        // 画像読み込み
        BufferedImage input = ImageIO.read(new File("path/filename_input" ));
        // 幅
        int width = input.getWidth();
        // 高さ
        int height = input.getHeight();
        // 出力用画像 ピクセルの初期値は透過なしの黒, 0x00000000
        BufferedImage output = createImage(width, height);
        int[][] check = new int[width][height];

        // 色の種類を何分の1に削減するか
        int colordev = 1;

        // RGB抽出用
        int mod = 0x100;

        // 円の半径
        int r = 2;
        // サンプリングする点の数は円の面積に反比例
        // 面積に適当な定数（ここでは4）をかけて隙間どうしが繋がってしまわないように調節
        for (int i = 0; i < width * height / (r * r * 4); i++) {
            int w = rand.nextInt(width);
            int h = rand.nextInt(height);

            // 色の平均計算用
            int count = 0;
            int r_ave = 0;
            int g_ave = 0;
            int b_ave = 0;

            // 楕円の大きさ（縦横の長さ）をr ± delta, delta < 10 の範囲でランダムに変える
            int delta = 10;
            int delta_w = rand.nextInt(delta);
            int delta_h = rand.nextInt(delta);

            // 操作対象とする楕円の縦軸と横軸
            int axis_w = r + delta_w;
            int axis_h = r + delta_h;


            // 真円のときのみdelta_h=delta_w
            // 上書きフラグ
            // 他の楕円と重なるときに上書きするかどうか選択
            int uwagaki = rand.nextInt(2);

            // 塗りつぶすピクセルの記録
            Queue<Point> to_be_painted = new ArrayDeque<>();

            // 楕円を適当に回転させることで絵画っぽい筆致にしたい
            // 回転角phiを設定(0<=phi<PI)
            double phi = rand.nextDouble() * PI;

            // 処理のための矩形を選択
            // 楕円はこの中に収まる
            int range = max(r + delta_w, r + delta_h);

            // 矩形の中のピクセルについて、+phiだけ回転させた楕円の内部にあるのか外部にあるのかを判定
            for (int iw = -range; iw <= range; iw++) {
                for (int ih = -range; ih <= range; ih++) {
                    double d_w = iw;
                    double d_h = ih;
                    // 矩形の中のピクセルを端から順に選ぶ
                    Point2D p2d = new Point2D.Double(d_w, d_h);
                    // ピクセルを逆回転させる
                    // 楕円ではなく判定対象のピクセルを回転させることで回転前は内側にあったかどうかを判定
                    Point2D p2d_r = rotate(p2d, -phi);

                    // x座標の位置から楕円のy座標の上限/下限がわかる。1 - (pow(p2d_r.getX() / axis_w, 2) <= 0の時は境界線上かそれよりも外側。
                    double h_border = axis_h * sqrt(1 - pow(p2d_r.getX() / axis_w, 2) > 0 ? 1 - pow(p2d_r.getX() / axis_w, 2) : 0);

                    // 逆回転した点が楕円の中にある ∧ 画像からはみ出していない ∧ （いまだに塗りつぶされていない ∨ 上書きすることになっている）
                    if ((-h_border < p2d_r.getY() && p2d_r.getY() < h_border) && (w + iw < width && h + ih < height) && (w + iw >= 0 && h + ih >= 0) && (check[w + iw][h + ih] == 0 || uwagaki == 1)) {
                        to_be_painted.add(new Point(w + iw, h + ih));
                        // 色の抽出
                        int color = input.getRGB(w + iw, h + ih);
                        int blue = (color % mod + mod) % mod;
                        color = (color - blue) / 0x100;
                        int green = (color % mod + mod) % mod;
                        color = (color - green) / 0x100;
                        int red = (color % mod + mod) % mod;
                        r_ave += red;
                        g_ave += green;
                        b_ave += blue;
                        count++;
                        check[w + iw][h + ih] = 1;
                    }
                }
            }

            if (count > 0) {
                r_ave /= count;
                g_ave /= count;
                b_ave /= count;
                while (to_be_painted.size() > 0) {
                    Point tbp = to_be_painted.poll();
                    // 色空間（1色なので数直線）をintのcolordevで割ってから再度かけることで圧縮
                    output.setRGB(tbp.x, tbp.y, 0xff000000 + 0x10000 * ((r_ave / colordev) * colordev) + 0x100 * ((g_ave / colordev) * colordev) + ((b_ave / colordev) * colordev));
                }
            }

        }

        // BFSによる隙間埋め
        // 各ピクセルを見て塗られていないピクセルがあればそこからBFSを行う
        // 隣接する隙間を抜き出してその中の色の平均値で塗りつぶす
        int[] dw = {-1, 0, 1, 0};
        int[] dh = {0, 1, 0, -1};
        for (int w = 0; w < width; w++) {
            for (int h = 0; h < height; h++) {

                if (check[w][h] == 0) {

                    int count = 0;
                    int r_ave = 0;
                    int g_ave = 0;
                    int b_ave = 0;

                    // 塗りつぶし用の記録
                    Queue<Point> pre = new ArrayDeque<>();
                    // BFS用
                    Queue<Point> q = new ArrayDeque<>();
                    q.add(new Point(w, h));
                    pre.add(new Point(w, h));
                    while (q.size() > 0) {
                        Point p = q.poll();
                        check[w][h] = 1;
                        for (int i = 0; i < 4; i++) {
                            int x = p.x + dw[i];
                            int y = p.y + dh[i];
                            if (0 <= x && 0 <= y && x < width && y < height) {
                                if (check[x][y] == 0) {
                                    check[x][y] = 1;

                                    int color = input.getRGB(x, y);
                                    int blue = (color % mod + mod) % mod;
                                    color = (color - blue) / 0x100;
                                    int green = (color % mod + mod) % mod;
                                    color = (color - green) / 0x100;
                                    int red = (color % mod + mod) % mod;

                                    r_ave += red;
                                    g_ave += green;
                                    b_ave += blue;
                                    count++;

                                    q.add(new Point(x, y));
                                    pre.add(new Point(x, y));
                                }
                            }
                        }
                    }
                    if (count > 0) {
                        r_ave /= count;
                        g_ave /= count;
                        b_ave /= count;
                        while (pre.size() > 0) {
                            Point pre_p = pre.poll();
                            output.setRGB(pre_p.x, pre_p.y, 0xff000000 + 0x10000 * ((r_ave / colordev) * colordev) + 0x100 * ((g_ave / colordev) * colordev) + ((b_ave / colordev) * colordev));
                        }
                    }
                }
            }
        }
        save(output, new File("path/filename_output"));
    }

    public static BufferedImage createImage(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    }

    public static void save(BufferedImage img, File f) throws IOException {
        if (!ImageIO.write(img, "PNG", f)) {
            throw new IOException("フォーマットが対象外");
        }
    }

    public static Point2D rotate(Point2D p2d, double angle) {
        return new Point2D.Double(p2d.getX() * cos(angle) - p2d.getY() * sin(angle), p2d.getY() * cos(angle) + p2d.getX() * sin(angle));
    }
}
