package util;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.util.internal.StringUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * JsonUtil 格式校验bean
 */
public class JsonUtil {
	private static ObjectMapper objectMapper = null;

	static {
		if (objectMapper == null) {
			objectMapper = new ObjectMapper();
		}
		objectMapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
		objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
	}

	/**
	 * 对JSON每一个KEY的VALUE文本中可能引起问题的特殊字符串做JS转义 双引号,单引号都转义
	 * @param json
	 * @return
	 */
	public static String escapeValue(String json) {
		if (!isJson(json)) {return null;}

		json = json.replaceAll("\\\\", "\\\\\\\\");
		json = json.replaceAll("\"", "\\\\\"");
		json = json.replaceAll("'", "\\\\'");
		json = json.replaceAll("\b", "\\\\b");
		json = json.replaceAll("\n", "\\\\n");
		json = json.replaceAll("\t", "\\\\t");
		json = json.replaceAll("\f", "\\\\f");
		json = json.replaceAll("\r", "\\\\r");

		return json;
	}

	/**
	 * 将一组key,value数据转换为JSON格式。注意：VALUE中的特殊字符串已做了JS转义。 例如： 输入："name","flouny"
	 * 输出："name:flouny"
	 * @param key
	 * @param value
	 * @return
	 */
	public static String toKeyValue(String key, String value) {
		if (StringUtil.isNullOrEmpty(key))  {throw new RuntimeException("The key of json-string not exist!");}

		return (value == null)?key.concat(":null") : key.concat(":\"").concat(escapeValue(value)).concat( "\"");
	}

	/**
	 * 返回 JSON nodes
	 * @param json
	 * @return
	 */
	public static JsonNode getJsonNode(String json) {
		if (!isJson(json)) {return null;}
		try {
			return objectMapper.readTree(json);
		} catch (IOException e) {
			return null;
		}
	}

	/**
	 * 返回JSON Object
	 * @param json
	 * @param clazz
	 * @return
	 */
	public static <T> T toBean(String json, Class<T> clazz) {
		if (StringUtil.isNullOrEmpty(json) || !isObjectJson(json) || clazz == null) {return null;}
		if (!isJson(json)) { return null;}
		try {
			return objectMapper.readValue(json, clazz);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * JSON 转换 HashMap
	 * @param json
	 * @return
	 */
	public static HashMap<?, ?> toMap(String json) {
		if (!isJson(json)) { return null;}
		try {
			return objectMapper.readValue(json, HashMap.class);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * JSON 转换 ArrayList
	 * @param json
	 * @return
	 */
	public static ArrayList<?> toList(String json) {
		if (!isJson(json)) {return null;}

		try {
			return objectMapper.readValue(json, ArrayList.class);
		} catch (Exception e) {

			return null;
		}
	}

	public static ArrayList<?> toList(String json, Class<?> clazz) {
		if (!isJson(json)) {return null;}

		try {
			return objectMapper.readValue(json, getCollectionType(ArrayList.class, (clazz == null)?HashMap.class:clazz));
		} catch (Exception e) {

			return null;
		}
	}

	/**
	 * 返回JSON String
	 * @param obj
	 * @return
	 */
	public static String toString(Object obj) {
		if (obj == null) {return null;}

		try {
			return objectMapper.writeValueAsString(obj);
		} catch (Exception e) {

			return null;
		}
	}

	/**
	 * 判断是否是JSON数组
	 * @param json
	 * @return
	 */
	public static boolean isArrayJson(String json) {
		if (!isJson(json)) { return false;}
		try {
			return objectMapper.readTree(json).isArray();
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * 判断是否是JSON对象
	 * @param json
	 * @return
	 */
	public static boolean isObjectJson(String json) {
		if (!isJson(json)) {return false;}
		try {
			return objectMapper.readTree(json).isObject();
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * 判断是否是JSON
	 * @param json
	 * @return
	 */
	public static boolean isJson(String json) {
		if (StringUtil.isNullOrEmpty(json)) { return false;}
		try {
			objectMapper.readTree(json);
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	private static JavaType getCollectionType(Class<?> collectionClass, Class<?>... elementClasses) {

		return objectMapper.getTypeFactory().constructParametrizedType(collectionClass, collectionClass, elementClasses);
	}
	//json格式验证
	public static JSONObject checkJson(String requestJsonString){
		JSONObject requestJson = null;
		try {
			requestJson = JSONObject.parseObject(requestJsonString);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return requestJson;
	}
}
