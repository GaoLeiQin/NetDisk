package netty.upload;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.*;
import io.netty.util.CharsetUtil;

import java.io.File;
import java.io.IOException;
import java.net.URI;

import static io.netty.buffer.Unpooled.copiedBuffer;

/**
 * Created by 白夜行 on 2017/6/7.
 */
public class MyHttpUploadServerHandler extends SimpleChannelInboundHandler<HttpObject> {

    private HttpRequest request;
    private final StringBuilder sbresponsetext = new StringBuilder();
    private HttpPostRequestDecoder decoder;
    private static final HttpDataFactory factory =
            new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE); // 最小值为16384L

    //设置浏览器文件上传的路径
    static {
        DiskFileUpload.deleteOnExitTemporaryFile = true;
        DiskFileUpload.baseDirectory = "G:"+ File.separatorChar+"HttpUpload";
        DiskAttribute.deleteOnExitTemporaryFile = true;
        DiskAttribute.baseDirectory = "G:"+ File.separatorChar+"HttpUpload";
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        //判断msg是否是HttpRequest类型
        if (msg instanceof HttpRequest) {
            //将HttpObject强转为HttpRequest类型
            HttpRequest request = this.request = (HttpRequest) msg;
            //将HttpRequest的路径String转换为URI类型
            URI uri = new URI(request.uri());
            //判断uri是否以/form开头
            if (!uri.getPath().startsWith("/form")) {
                // 构建表单
                writeMenu(ctx);
                return;
            }

            //清空StringBuilder
            sbresponsetext.setLength(0);
            sbresponsetext.append("白夜行网盘 欢迎您 ！");

            //创建HttpPostRequestDecoder
            try {
                decoder = new HttpPostRequestDecoder(factory, request);
            } catch (HttpPostRequestDecoder.ErrorDataDecoderException e) {
                e.printStackTrace();
                sbresponsetext.append(e.getMessage());
                //若发生解码异常，则发送响应给浏览器
                writeResponse(ctx.channel());
                //关闭通道
                ctx.channel().close();
                System.out.println("创建HttpPostRequestDecoder 出错 ！");
                return;
            }
        }

        //判断decoder之前是否被创建
        if (decoder != null) {
            if (msg instanceof HttpContent) {
                //若msg是HttpContent类型，则强转为HttpContent
                HttpContent chunk = (HttpContent) msg;
                try {
                    //获取chunk以初始化decoder
                    decoder.offer(chunk);
                } catch (HttpPostRequestDecoder.ErrorDataDecoderException e1) {
                    e1.printStackTrace();
                    sbresponsetext.append(e1.getMessage());
                    writeResponse(ctx.channel());
                    ctx.channel().close();
                    System.out.println("decoder.offer(chunk) 错误！");
                    return;
                }
                readHttpDataChunkByChunk();
                // example of reading only if at the end
                if (chunk instanceof LastHttpContent) {
                    writeResponse(ctx.channel());
                    reset();
                }
            }
        } else {
            writeResponse(ctx.channel());
        }
    }

    private void reset() {
        request = null;

        //销毁decoder并释放所有资源
        decoder.destroy();
        decoder = null;
    }

    private void writeMenu(ChannelHandlerContext ctx) {
        // 清空StringBuilder
        sbresponsetext.setLength(0);

        //创建菜单
        sbresponsetext.append("<html>");
        sbresponsetext.append("<head>");
        sbresponsetext.append("<title>NetDisk</title>\r\n");
        sbresponsetext.append("<style type=\"text/css\">body {\r\n");
        sbresponsetext.append("background-image:url(\"../../src/image/网盘一.jpg\");");
        sbresponsetext.append("background-repeat:repeat-y;\n");
        sbresponsetext.append("background-position:center;}</style>");
        sbresponsetext.append("</head>\r\n");
        sbresponsetext.append("<body>");

        sbresponsetext.append("<FORM ACTION=\"/formpostmultipart\" ENCTYPE=\"multipart/form-data\" METHOD=\"POST\">");
        sbresponsetext.append("<input type=hidden name=getform value=\"POST\">");
        sbresponsetext.append("<table border=\"0\">");
        sbresponsetext.append("<tr><td><font size=\"5\" color=\"#7fff00\">备注 ： <br> <textarea name=\"thirdinfo\" cols=40 rows=10></textarea>");
        sbresponsetext.append("<tr><td><font size=\"10\" color=\"#7fff00\">选择文件 : <br> <input type=file name=\"myfile\"></td></tr>");
        sbresponsetext.append("<tr><td><INPUT TYPE=\"submit\" NAME=\"Send\" VALUE=\"上传文件\"></INPUT></td>");
        sbresponsetext.append("<td><INPUT TYPE=\"reset\" NAME=\"Clear\" VALUE=\"清除\" ></INPUT></td></tr>");
        sbresponsetext.append("</table></FORM>\r\n");

        sbresponsetext.append("</body>");
        sbresponsetext.append("</html>");


        //将StringBuilder对象转换为ByteBuff
        ByteBuf buf = copiedBuffer(sbresponsetext.toString(), CharsetUtil.UTF_8);
        //建立响应对象
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);

        //设置响应头信息，服务器给浏览器发送的数据类型；回送数据长度.
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, buf.readableBytes());

        // 将响应写入管道中并刷新
        ctx.channel().writeAndFlush(response);
    }

    private void writeResponse(Channel channel) {
        //将StringBuilder转换为ByteBuf类型
        ByteBuf buf = copiedBuffer(sbresponsetext.toString(), CharsetUtil.UTF_8);
        //清空StringBuilder
        sbresponsetext.setLength(0);


        //判断是否关闭连接
        boolean close = request.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true)
                || request.protocolVersion().equals(HttpVersion.HTTP_1_0)
                && !request.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE, true);

        //建立响应对象
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);
        //设置头信息：数据类型
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");

        //若不关闭连接，则添加数据长度头信息，若关闭连接，则为最后一次响应，不需要数据长度头信息
        if (!close) {
            response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, buf.readableBytes());
        }

        //向浏览器发送响应
        ChannelFuture future = channel.writeAndFlush(response);
        //在写操作完成后，若需要关闭，就关闭。
        if (close) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }

    private void readHttpDataChunkByChunk() {
        try {
            while (decoder.hasNext()) {
                InterfaceHttpData data = decoder.next();
                if (data != null) {
                    try {
                        //将data写入磁盘
                        writeHttpData(data);
                    } finally {
                        data.release();
                    }
                }
            }
        } catch (HttpPostRequestDecoder.EndOfDataDecoderException e1) {
            sbresponsetext.append("\r\n\r\n传输已完成 ！ \r\n\r\n");
        }
    }

    private void writeHttpData(InterfaceHttpData data) {
        if (data.getHttpDataType() == InterfaceHttpData.HttpDataType.Attribute) {
            //如果data是Attribute类型就强制转型
            Attribute attribute = (Attribute) data;
            //属性值
            String value;
            try {
                value = attribute.getValue();
            } catch (IOException e1) {
                e1.printStackTrace();
                sbresponsetext.append("\r\n error");
                System.out.println("attribute.getValue 异常 ！");
                return;
            }
            //对于文件大小的判断
            if (value.length() > 1000) {
                sbresponsetext.append("\r\n 这个文件有点大呦 ！\r\n");
            } else {
//                System.out.println("传输中......");
            }
        } else {
            if (data.getHttpDataType() == InterfaceHttpData.HttpDataType.FileUpload) {
                //若data是FileUpload就强制转型
                FileUpload fileUpload = (FileUpload) data;
                //若文件上传已完成
                if (fileUpload.isCompleted()) {
                    //对上传的文件大小判断
                    if (fileUpload.length() < 10000) {
                        sbresponsetext.append(" \r\n 小文件 So easy ！\r\n");
                    } else {
                        sbresponsetext.append(" \r\n 文件有点大呀 ！ " + fileUpload.length() + "\r\n");
                        System.out.println("文件已完成传输 ！");
                    }
                    try {
                        //将上传的文件转存到本地磁盘上
                        File file = new File(fileUpload.getFile().getParentFile()+File.separator+fileUpload.getFilename());
                        fileUpload.renameTo(file);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                } else {
                    sbresponsetext.append("\t文件上传出错了 ! \r\n");
                }
            }
        }
    }


    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (decoder != null) {
            decoder.cleanFiles();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.channel().close();
    }


}
