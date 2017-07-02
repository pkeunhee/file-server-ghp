package kr.pe.ghp.fileserver.server;

import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static io.netty.handler.codec.http.HttpHeaders.setContentLength;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.Map;

import javax.activation.MimetypesFileTypeMap;
import javax.imageio.ImageIO;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.imgscalr.Scalr;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.util.CharsetUtil;
import kr.pe.ghp.fileserver.util.PropertiesUtils;

/**
 * @author geunhui park
 */
public class HttpStaticFileServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
	private static Logger LOGGER = LoggerFactory.getLogger(HttpStaticFileServerHandler.class);

	private static final String PATH_PARENT = PropertiesUtils.getProperty("path.parent"); // 파일 저장될곳 parent path
	private static final String PATH_DIR = PropertiesUtils.getProperty("path.dir");
	private static final String URL_PARENT = PropertiesUtils.getProperty("url.parent");

	// query param used to download a file
	private static final String FILE_QUERY_PARAM = "file";

	private HttpPostRequestDecoder decoder;
	private static final HttpDataFactory factory = new DefaultHttpDataFactory(true);

	private boolean readingChunks;

	private static final int THUMB_MAX_WIDTH = 100;
	private static final int THUMB_MAX_HEIGHT = 100;

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
		LOGGER.info("channelRead :: " + request.method() + " request received");

		if (request.method() == HttpMethod.GET) {
			serveFile(ctx, request); // user requested a file, serve it
		} else if (request.method() == HttpMethod.POST) {
			uploadFile(ctx, request); // user requested to upload file, handle request
		} else { // 잘못된 요청
			LOGGER.info(request.method() + " request received, sending 405");
			sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED);
		}
	}

	private void serveFile(ChannelHandlerContext ctx, FullHttpRequest request) {
		// decode the query string
		QueryStringDecoder decoderQuery = new QueryStringDecoder(request.getUri());
		Map<String, List<String>> uriAttributes = decoderQuery.parameters();

		// get the requested file name
		String fileName = "";
		try {
			fileName = uriAttributes.get(FILE_QUERY_PARAM).get(0);
		} catch (Exception e) {
			sendError(ctx, HttpResponseStatus.BAD_REQUEST, FILE_QUERY_PARAM + " query param not found");
			return;
		}

		// start serving the requested file
		sendFile(ctx, fileName, request);
	}

	/**
	 * This method reads the requested file from disk and sends it as response. It also sets proper content-type of the response header
	 *
	 * @param fileName
	 *            name of the requested file
	 */
	private void sendFile(ChannelHandlerContext ctx, String fileName, FullHttpRequest request) {
		File file = new File(PATH_PARENT + fileName);
		if (file.isDirectory() || file.isHidden() || !file.exists()) {
			sendError(ctx, NOT_FOUND);
			return;
		}

		if (!file.isFile()) {
			sendError(ctx, FORBIDDEN);
			return;
		}

		RandomAccessFile raf;

		try {
			raf = new RandomAccessFile(file, "r");
		} catch (FileNotFoundException fnfe) {
			sendError(ctx, NOT_FOUND);
			return;
		}

		long fileLength = 0;
		try {
			fileLength = raf.length();
		} catch (IOException ex) {
			LOGGER.error(ex.toString(), ex);
		}

		HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
		setContentLength(response, fileLength);
		setContentTypeHeader(response, file);

		// setDateAndCacheHeaders(response, file);
		if (isKeepAlive(request)) {
			response.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
		}

		// Write the initial line and the header.
		ctx.write(response);

		// Write the content.
		ChannelFuture sendFileFuture;
		DefaultFileRegion defaultRegion = new DefaultFileRegion(raf.getChannel(), 0, fileLength);
		sendFileFuture = ctx.write(defaultRegion);

		// Write the end marker
		ChannelFuture lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);

		// Decide whether to close the connection or not.
		if (!isKeepAlive(request)) {
			// Close the connection when the whole content is written out.
			lastContentFuture.addListener(ChannelFutureListener.CLOSE);
		}
	}

	/**
	 * This will set the content types of files. If you want to support any files add the content type and corresponding file extension here.
	 *
	 * @param response
	 * @param file
	 */
	private static void setContentTypeHeader(HttpResponse response, File file) {
		MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
		mimeTypesMap.addMimeTypes("image png tif jpg jpeg bmp");
		mimeTypesMap.addMimeTypes("text/plain txt");
		mimeTypesMap.addMimeTypes("application/pdf pdf");

		String mimeType = mimeTypesMap.getContentType(file);

		response.headers().set(CONTENT_TYPE, mimeType);
	}

	private void uploadFile(ChannelHandlerContext ctx, FullHttpRequest request) {
		try {
			decoder = new HttpPostRequestDecoder(factory, request);
			LOGGER.info("decoder created");
		} catch (Exception e1) {
			LOGGER.error(e1.toString(), e1);
			sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Failed to decode file data");
			return;
		}

		readingChunks = HttpUtil.isTransferEncodingChunked(request);

		if (decoder == null) {
			sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Failed to decode file data");
			return;
		}

		if (!(request instanceof HttpContent)) {
			sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Not a http request");
			return;
		}

		// New chunk is received
		HttpContent chunk = (HttpContent) request;
		try {
			decoder.offer(chunk);
		} catch (Exception e1) {
			LOGGER.error(e1.toString(), e1);
			sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Failed to decode file data");
			return;
		}

		readHttpDataChunkByChunk(ctx);

		// example of reading only if at the end
		if (chunk instanceof LastHttpContent) {
			readingChunks = false;
			reset();
		}
	}

	private void sendOptionsRequestResponse(ChannelHandlerContext ctx) {
		HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
		ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
	}

	private void sendResponse(ChannelHandlerContext ctx, String responseString, String contentType, HttpResponseStatus status) {
		FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.copiedBuffer(responseString, CharsetUtil.UTF_8));

		response.headers().set(CONTENT_TYPE, contentType);
		response.headers().add("Access-Control-Allow-Origin", "*");

		// Close the connection as soon as the error message is sent.
		ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
	}

	private void sendUploadedFileName(JSONObject fileName, ChannelHandlerContext ctx) {
		String msg = "Unexpected error occurred";
		String contentType = "application/json; charset=UTF-8";
		HttpResponseStatus status = HttpResponseStatus.OK;

		if (fileName != null) {
			msg = fileName.toString();
		} else {
			LOGGER.error("uploaded file names are blank");
			status = HttpResponseStatus.BAD_REQUEST;
			contentType = "text/plain; charset=UTF-8";
		}

		sendResponse(ctx, msg, contentType, status);

	}

	private void reset() {
		// destroy the decoder to release all resources
		decoder.destroy();
		decoder = null;
	}

	/**
	 * Example of reading request by chunk and getting values from chunk to chunk
	 */
	private void readHttpDataChunkByChunk(ChannelHandlerContext ctx) {
		if (decoder.isMultipart()) {
			try {
				while (decoder.hasNext()) {
					LOGGER.info("chunk 단위로 request 를 읽는다.");
					InterfaceHttpData data = decoder.next();
					if (data != null) {
						writeHttpData(data, ctx);
						data.release();
					}
				}
			} catch (Exception e) {
				// TODO : decoder.hasNext() EndOfDataDecoderException 발생 함. (정상 동작이긴 함)
				// LOGGER.error(e.toString(), e);
			}
		} else {
			sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Not a multipart request");
		}

		// System.out.println("decoder has no next");
	}

	private void writeHttpData(InterfaceHttpData data, ChannelHandlerContext ctx) {
		if (data.getHttpDataType() == InterfaceHttpData.HttpDataType.FileUpload) {
			FileUpload fileUpload = (FileUpload) data;

			if (fileUpload.isCompleted()) {
				JSONObject json = saveFileToDisk(fileUpload);
				sendUploadedFileName(json, ctx);
			} else {
				// responseContent.append("\tFile to be continued but should not!\r\n");
				sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Unknown error occurred");
			}
		}
	}

	/**
	 * Saves the uploaded file to disk.
	 *
	 * @param fileUpload
	 *            FileUpload object that'll be saved
	 * @return name of the saved file. null if error occurred
	 */
	private JSONObject saveFileToDisk(FileUpload fileUpload) {
		String upoadedFileName = fileUpload.getFilename(); // 임시 디렉토리에 저장된 파일

		StringBuffer newFileName = new StringBuffer(); // 새로운 파일명으로 rename 하기 위해
		newFileName.append(FilenameUtils.getBaseName(upoadedFileName));
		newFileName.append("_").append(System.currentTimeMillis());
		newFileName.append(".").append(FilenameUtils.getExtension(upoadedFileName));

		// TODO : service 로 분리
		try {
			BufferedImage originalImage = ImageIO.read(fileUpload.getFile());

			// 기준 사이즈보다 큰 경우 resize 시켜준다.
			boolean isPortraitAndResizable = originalImage.getWidth() < originalImage.getHeight() && originalImage.getWidth() > 1000;
			boolean isLandscapeAndResizable = originalImage.getWidth() >= originalImage.getHeight() && originalImage.getWidth() > 1000;

			if (isPortraitAndResizable || isLandscapeAndResizable) {
				BufferedImage resizedImage = Scalr.resize(originalImage, Scalr.Method.ULTRA_QUALITY, Scalr.Mode.FIT_TO_WIDTH, 1000);
				// PNG 파일을 리사이즈 하는 경우 alpha 관련 문제로 배경 색상이 이상하게 되는 현상 방지를 위해
				BufferedImage imageToSave = new BufferedImage(resizedImage.getWidth(), resizedImage.getHeight(), BufferedImage.TYPE_INT_RGB);
				Graphics g = imageToSave.getGraphics();
				g.drawImage(resizedImage, 0, 0, null);

				ImageIO.write(imageToSave, "jpg", fileUpload.getFile());
			}
		} catch (Exception e) {
			LOGGER.error(e.toString(), e);
		}

		JSONObject responseJson = new JSONObject();
		try {
			StringBuffer newFilePath = new StringBuffer();
			newFilePath.append(PATH_PARENT);
			newFilePath.append(StringUtils.endsWith(PATH_PARENT, "/") ? "" : "/");
			newFilePath.append(PATH_DIR);
			newFilePath.append("/");
			newFilePath.append(newFileName.toString());

			fileUpload.renameTo(new File(newFilePath.toString())); // 파일을 이동 시킨다
			responseJson.put("url", URL_PARENT + PATH_DIR + "/" + newFileName.toString());
		} catch (Exception e) {
			LOGGER.error(e.toString(), e);
			responseJson = null;
		}

		return responseJson;
	}

	private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status, String msg) {
		FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.copiedBuffer(msg, CharsetUtil.UTF_8));
		response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");

		// Close the connection as soon as the error message is sent.
		ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
	}

	private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
		sendError(ctx, status, "Failure: " + status.toString() + "\r\n");
	}
}