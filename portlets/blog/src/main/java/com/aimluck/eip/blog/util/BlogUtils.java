/*
 * Aipo is a groupware program developed by Aimluck,Inc.
 * Copyright (C) 2004-2011 Aimluck,Inc.
 * http://www.aipo.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.aimluck.eip.blog.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.cayenne.DataRow;
import org.apache.cayenne.exp.Expression;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.jetspeed.om.profile.Entry;
import org.apache.jetspeed.om.profile.Portlets;
import org.apache.jetspeed.om.security.UserIdPrincipal;
import org.apache.jetspeed.services.JetspeedSecurity;
import org.apache.jetspeed.services.logging.JetspeedLogFactoryService;
import org.apache.jetspeed.services.logging.JetspeedLogger;
import org.apache.jetspeed.services.resources.JetspeedResources;
import org.apache.jetspeed.services.rundata.JetspeedRunData;
import org.apache.jetspeed.util.template.JetspeedLink;
import org.apache.jetspeed.util.template.JetspeedLinkFactory;
import org.apache.turbine.services.TurbineServices;
import org.apache.turbine.util.DynamicURI;
import org.apache.turbine.util.RunData;
import org.apache.velocity.context.Context;

import com.aimluck.commons.field.ALDateTimeField;
import com.aimluck.eip.blog.BlogThemaResultData;
import com.aimluck.eip.blog.BlogUserResultData;
import com.aimluck.eip.cayenne.om.portlet.EipTBlog;
import com.aimluck.eip.cayenne.om.portlet.EipTBlogComment;
import com.aimluck.eip.cayenne.om.portlet.EipTBlogEntry;
import com.aimluck.eip.cayenne.om.portlet.EipTBlogFile;
import com.aimluck.eip.cayenne.om.portlet.EipTBlogThema;
import com.aimluck.eip.cayenne.om.security.TurbineUser;
import com.aimluck.eip.common.ALBaseUser;
import com.aimluck.eip.common.ALDBErrorException;
import com.aimluck.eip.common.ALEipConstants;
import com.aimluck.eip.common.ALPageNotFoundException;
import com.aimluck.eip.fileupload.beans.FileuploadBean;
import com.aimluck.eip.fileupload.beans.FileuploadLiteBean;
import com.aimluck.eip.fileupload.util.FileuploadUtils;
import com.aimluck.eip.orm.Database;
import com.aimluck.eip.orm.query.SelectQuery;
import com.aimluck.eip.services.accessctl.ALAccessControlConstants;
import com.aimluck.eip.services.accessctl.ALAccessControlFactoryService;
import com.aimluck.eip.services.accessctl.ALAccessControlHandler;
import com.aimluck.eip.services.social.ALActivityService;
import com.aimluck.eip.services.social.model.ALActivityPutRequest;
import com.aimluck.eip.services.storage.ALStorageService;
import com.aimluck.eip.util.ALCommonUtils;
import com.aimluck.eip.util.ALEipUtils;

/**
 * ブログのユーティリティクラスです。 <BR>
 * 
 */
public class BlogUtils {

  /** logger */
  private static final JetspeedLogger logger = JetspeedLogFactoryService
    .getLogger(BlogUtils.class.getName());

  public static final String DATE_TIME_FORMAT =
    ALDateTimeField.DEFAULT_DATE_TIME_FORMAT;

  public static final String TARGET_GROUP_NAME = "target_group_name";

  /** 所有者の識別子 */
  public static final String OWNER_ID = "ownerid";

  /** 検索キーワードの識別子 */
  public static final String SEARCH_WORD = "keyword";

  /** グループの識別子 */
  public static final String GROUP_ID = "groupid";

  /** テーマの識別子 */
  public static final String THEME_ID = "themeid";

  /** 一時添付ファイル名 */
  public static final String ATTACHMENT_TEMP_FILENAME = "file";

  /** 一時添付ファイル名を記録するファイル名 */
  public static final String ATTACHMENT_TEMP_FILENAME_REMAIND = "file.txt";

  /** デフォルトエンコーディングを表わすシステムプロパティのキー */
  public static final String FILE_ENCODING = JetspeedResources.getString(
    "content.defaultencoding",
    "UTF-8");

  /** ブログの添付ファイルを保管するディレクトリの指定 */
  public static final String FOLDER_FILEDIR_BLOG = JetspeedResources.getString(
    "aipo.filedir",
    "");

  /** ブログの添付ファイルを保管するディレクトリのカテゴリキーの指定 */
  public static final String CATEGORY_KEY = JetspeedResources.getString(
    "aipo.blog.categorykey",
    "");

  /** データベースに登録されたファイルを表す識別子 */
  public static final String PREFIX_DBFILE = "DBF";

  public static final String BLOG_PORTLET_NAME = "Blog";

  /**
   * エントリーオブジェクトモデルを取得します。 <BR>
   * 
   * @param rundata
   * @param context
   * @param isJoin
   *          テーマテーブルをJOINするかどうか
   * @return
   * @throws ALDBErrorException
   */
  public static EipTBlogEntry getEipTBlogEntry(RunData rundata, Context context)
      throws ALDBErrorException {
    String entryid =
      ALEipUtils.getTemp(rundata, context, ALEipConstants.ENTITY_ID);
    try {
      if (entryid == null || Integer.valueOf(entryid) == null) {
        // Todo IDが空の場合
        logger.debug("[Blog Entry] Empty ID...");
        return null;
      }

      SelectQuery<EipTBlogEntry> query = Database.query(EipTBlogEntry.class);
      Expression exp =
        ExpressionFactory.matchDbExp(EipTBlogEntry.ENTRY_ID_PK_COLUMN, Integer
          .valueOf(entryid));
      query.setQualifier(exp);
      List<EipTBlogEntry> entrys = query.fetchList();
      if (entrys == null || entrys.size() == 0) {
        // 指定したエントリーIDのレコードが見つからない場合
        logger.debug("[Blog Entry] Not found ID...");
        throw new ALPageNotFoundException();
      }
      return entrys.get(0);
    } catch (Exception ex) {
      logger.error("Exception", ex);
      throw new ALDBErrorException();
    }
  }

  /**
   * ブログカテゴリ オブジェクトモデルを取得します。 <BR>
   * 
   * @param rundata
   * @param context
   * @return
   */
  public static EipTBlogThema getEipTBlogThema(RunData rundata, Context context) {
    String themaid =
      ALEipUtils.getTemp(rundata, context, ALEipConstants.ENTITY_ID);
    try {
      if (themaid == null || Integer.valueOf(themaid) == null) {
        // カテゴリIDが空の場合
        logger.debug("[Blog] Empty ID...");
        return null;
      }

      SelectQuery<EipTBlogThema> query = Database.query(EipTBlogThema.class);
      Expression exp =
        ExpressionFactory.matchDbExp(EipTBlogThema.THEMA_ID_PK_COLUMN, Integer
          .valueOf(themaid));
      query.setQualifier(exp);
      List<EipTBlogThema> themas = query.fetchList();
      if (themas == null || themas.size() == 0) {
        // 指定したテーマIDのレコードが見つからない場合
        logger.debug("[Blog] Not found ID...");
        return null;
      }
      return themas.get(0);
    } catch (Exception ex) {
      logger.error("Exception", ex);
      return null;
    }
  }

  /**
   * ブログカテゴリ オブジェクトモデルを取得します。 <BR>
   * 
   * @param rundata
   * @param context
   * @return
   */
  public static EipTBlogThema getEipTBlogThema(Long thema_id) {
    try {
      EipTBlogThema thema = Database.get(EipTBlogThema.class, thema_id);
      return thema;
    } catch (Exception ex) {
      logger.error("Exception", ex);
      return null;
    }
  }

  public static EipTBlog getEipTBlog(RunData rundata, Context context) {
    try {
      int uid = ALEipUtils.getUserId(rundata);
      SelectQuery<EipTBlog> query = Database.query(EipTBlog.class);
      Expression exp =
        ExpressionFactory.matchExp(EipTBlog.OWNER_ID_PROPERTY, Integer
          .valueOf(uid));
      query.setQualifier(exp);
      List<EipTBlog> blogs = query.fetchList();
      if (blogs == null || blogs.size() == 0) {
        // 指定したブログIDのレコードが見つからない場合
        logger.debug("[Blog Entry] Not found ID...");
        return null;
      }
      return blogs.get(0);
    } catch (Exception ex) {
      logger.error("Exception", ex);
      return null;
    }
  }

  /**
   * コメントオブジェクトモデルを取得します。 <BR>
   * 
   * @param rundata
   * @param context
   * @return
   */
  public static EipTBlogComment getEipTBlogComment(RunData rundata,
      Context context, String commentid) throws ALPageNotFoundException,
      ALDBErrorException {
    try {
      if (commentid == null || Integer.valueOf(commentid) == null) {
        // トピック ID が空の場合
        logger.debug("[BlogUtils] Empty ID...");
        throw new ALPageNotFoundException();
      }

      int userid = ALEipUtils.getUserId(rundata);
      SelectQuery<EipTBlogComment> query =
        Database.query(EipTBlogComment.class);
      Expression exp1 =
        ExpressionFactory.matchDbExp(
          EipTBlogComment.COMMENT_ID_PK_COLUMN,
          Integer.valueOf(commentid));
      query.setQualifier(exp1);
      List<EipTBlogComment> comments = query.fetchList();
      if (comments == null || comments.size() == 0) {
        // 指定した トピック ID のレコードが見つからない場合
        logger.debug("[BlogUtils] Not found ID...");
        throw new ALPageNotFoundException();
      }
      return comments.get(0);
    } catch (Exception ex) {
      logger.error("[BlogUtils]", ex);
      throw new ALDBErrorException();

    }
  }

  /**
   * ファイルオブジェクトモデルを取得します。 <BR>
   * 
   * @param rundata
   * @param context
   * @return
   */
  public static EipTBlogFile getEipTBlogFile(RunData rundata)
      throws ALPageNotFoundException, ALDBErrorException {
    try {
      int ownerid = rundata.getParameters().getInt(BlogUtils.OWNER_ID, -1);
      int entryid =
        rundata.getParameters().getInt(ALEipConstants.ENTITY_ID, -1);
      int fileid = rundata.getParameters().getInt("attachmentIndex", -1);
      if (ownerid <= 0 || entryid <= 0 || fileid <= 0) {
        // トピック ID が空の場合
        logger.debug("[BlogUtils] Empty ID...");
        throw new ALPageNotFoundException();
      }

      SelectQuery<EipTBlogFile> query = Database.query(EipTBlogFile.class);
      Expression exp1 =
        ExpressionFactory.matchDbExp(EipTBlogFile.FILE_ID_PK_COLUMN, Integer
          .valueOf(fileid));
      query.setQualifier(exp1);
      Expression exp2 =
        ExpressionFactory.matchExp(EipTBlogFile.OWNER_ID_PROPERTY, Integer
          .valueOf(ownerid));
      query.andQualifier(exp2);
      Expression exp3 =
        ExpressionFactory.matchDbExp(EipTBlogEntry.ENTRY_ID_PK_COLUMN, Integer
          .valueOf(entryid));
      query.andQualifier(exp3);

      List<EipTBlogFile> files = query.fetchList();

      if (files == null || files.size() == 0) {
        // 指定した トピック ID のレコードが見つからない場合
        logger.debug("[BlogUtils] Not found ID...");
        throw new ALPageNotFoundException();
      }
      return files.get(0);
    } catch (Exception ex) {
      logger.error("[BlogUtils]", ex);
      throw new ALDBErrorException();

    }
  }

  /**
   * トピックオブジェクトモデルを取得します。 <BR>
   * 
   * @param rundata
   * @param context
   * @param isJoin
   *          カテゴリテーブルをJOINするかどうか
   * @return
   */
  public static EipTBlogEntry getEipTBlogParentEntry(RunData rundata,
      Context context) throws ALPageNotFoundException, ALDBErrorException {
    String entryid =
      ALEipUtils.getTemp(rundata, context, ALEipConstants.ENTITY_ID);
    try {
      if (entryid == null || Integer.valueOf(entryid) == null) {
        // トピック ID が空の場合
        logger.debug("[BlogUtil] Empty ID...");
        throw new ALPageNotFoundException();
      }

      SelectQuery<EipTBlogEntry> query = Database.query(EipTBlogEntry.class);
      Expression exp =
        ExpressionFactory.matchDbExp(EipTBlogEntry.ENTRY_ID_PK_COLUMN, Integer
          .valueOf(entryid));
      query.setQualifier(exp);
      List<EipTBlogEntry> entrys = query.fetchList();
      if (entrys == null || entrys.size() == 0) {
        // 指定した トピック ID のレコードが見つからない場合
        logger.debug("[BlogUtils] Not found ID...");
        throw new ALPageNotFoundException();
      }

      EipTBlogEntry entry = entrys.get(0);
      return entry;
    } catch (Exception ex) {
      logger.error("[EntryUtils]", ex);
      throw new ALDBErrorException();

    }
  }

  public static List<BlogThemaResultData> getThemaList(RunData rundata,
      Context context) {
    // カテゴリ一覧
    ArrayList<BlogThemaResultData> themaList =
      new ArrayList<BlogThemaResultData>();
    try {
      SelectQuery<EipTBlogThema> query = Database.query(EipTBlogThema.class);
      query.orderAscending(EipTBlogThema.THEMA_NAME_PROPERTY);
      List<EipTBlogThema> aList = query.fetchList();
      int size = aList.size();
      BlogThemaResultData lastRd = new BlogThemaResultData();
      lastRd.initField();
      for (int i = 0; i < size; i++) {
        EipTBlogThema record = aList.get(i);
        if (record.getThemaId() != 1) {
          BlogThemaResultData rd = new BlogThemaResultData();
          rd.initField();
          rd.setThemaId(record.getThemaId().longValue());
          rd.setThemaName(record.getThemaName());
          themaList.add(rd);
        } else {
          lastRd.setThemaId(record.getThemaId().longValue());
          lastRd.setThemaName(record.getThemaName());
        }
      }
      themaList.add(lastRd);

    } catch (Exception ex) {
      logger.error("Exception", ex);
      return null;
    }
    return themaList;
  }

  public static String getTargetGroupName(RunData rundata, Context context) {
    String target_group_name = null;
    String idParam = rundata.getParameters().getString(TARGET_GROUP_NAME);
    target_group_name = ALEipUtils.getTemp(rundata, context, TARGET_GROUP_NAME);
    if (idParam == null && target_group_name == null) {
      ALEipUtils.setTemp(rundata, context, TARGET_GROUP_NAME, "all");
      target_group_name = "all";
    } else if (idParam != null) {
      ALEipUtils.setTemp(rundata, context, TARGET_GROUP_NAME, idParam);
      target_group_name = idParam;
    }
    return target_group_name;
  }

  /**
   * 顔写真の有無の情報をもつユーザオブジェクトの一覧を取得する．
   * 
   * @param org_id
   * @param groupname
   * @return
   */
  public static List<BlogUserResultData> getBlogUserResultDataList(
      String groupname) {
    List<BlogUserResultData> list = new ArrayList<BlogUserResultData>();

    // SQLの作成
    StringBuffer statement = new StringBuffer();
    statement.append("SELECT DISTINCT ");
    statement
      .append("  B.USER_ID, B.LOGIN_NAME, B.FIRST_NAME, B.LAST_NAME, B.PHOTO, D.POSITION ");
    statement.append("FROM turbine_user_group_role as A ");
    statement.append("LEFT JOIN turbine_user as B ");
    statement.append("  on A.USER_ID = B.USER_ID ");
    statement.append("LEFT JOIN turbine_group as C ");
    statement.append("  on A.GROUP_ID = C.GROUP_ID ");
    statement.append("LEFT JOIN eip_m_user_position as D ");
    statement.append("  on A.USER_ID = D.USER_ID ");
    statement.append("WHERE B.USER_ID > 3 AND B.DISABLED = 'F'");
    statement.append(" AND C.GROUP_NAME = #bind($groupname) ");
    statement.append("ORDER BY D.POSITION");
    String query = statement.toString();

    try {
      // List ulist = BasePeer.executeQuery(query, org_id);

      List<DataRow> ulist =
        Database
          .sql(TurbineUser.class, query)
          .param("groupname", groupname)
          .fetchListAsDataRow();

      int recNum = ulist.size();

      DataRow dataRow;
      BlogUserResultData user;

      // ユーザデータを作成し、返却リストへ格納
      for (int j = 0; j < recNum; j++) {
        dataRow = ulist.get(j);
        user = new BlogUserResultData();
        user.initField();
        user.setUserId((Integer) Database.getFromDataRow(
          dataRow,
          TurbineUser.USER_ID_PK_COLUMN));
        user.setName((String) Database.getFromDataRow(
          dataRow,
          TurbineUser.LOGIN_NAME_COLUMN));
        user.setAliasName((String) Database.getFromDataRow(
          dataRow,
          TurbineUser.FIRST_NAME_COLUMN), (String) Database.getFromDataRow(
          dataRow,
          TurbineUser.LAST_NAME_COLUMN));
        byte[] photo =
          (byte[]) Database.getFromDataRow(dataRow, TurbineUser.PHOTO_COLUMN);

        if (photo != null && photo.length > 0) {
          user.setHasPhoto(true);
        } else {
          user.setHasPhoto(false);
        }
        list.add(user);
      }
    } catch (Exception ex) {
      logger.error("[BlogUtils]", ex);
    }
    return list;
  }

  /**
   * ユーザ情報の取得
   * 
   * @param userid
   *          ユーザID
   * @return
   */
  public static ALBaseUser getBaseUser(int userid) {
    String uid = String.valueOf(userid);
    try {
      if (uid == null) {
        logger.debug("Empty ID...");
        return null;
      }
      return (ALBaseUser) JetspeedSecurity.getUser(new UserIdPrincipal(uid));
    } catch (Exception ex) {
      logger.error("Exception", ex);
      return null;
    }
  }

  public static String getUserFullName(int userid) {
    String userName = "";
    ALBaseUser user = getBaseUser(userid);
    if (user != null) {
      userName =
        new StringBuffer().append(user.getLastName()).append(" ").append(
          user.getFirstName()).toString();
    }
    return userName;
  }

  public static String compressString(String src, int length) {
    if (src == null || src.length() == 0 || length <= 0) {
      return src;
    }

    String subject;
    if (src.length() > length) {
      subject = src.substring(0, length);
      subject += "・・・";
    } else {
      subject = src;
    }
    return subject;
  }

  /**
   * 指定したエントリー名を持つ個人設定ページに含まれるポートレットへの URI を取得する．
   * 
   * @param rundata
   * @param portletEntryName
   *          PSML ファイルに記述されているタグ entry の要素 parent
   * @return
   */
  public static String getPortletURIinPersonalConfigPane(RunData rundata,
      String portletEntryName) {
    try {
      Portlets portlets =
        ((JetspeedRunData) rundata).getProfile().getDocument().getPortlets();
      if (portlets == null) {
        return null;
      }

      Portlets[] portletList = portlets.getPortletsArray();
      if (portletList == null) {
        return null;
      }

      int length = portletList.length;
      for (int i = 0; i < length; i++) {
        Entry[] entries = portletList[i].getEntriesArray();
        if (entries == null || entries.length <= 0) {
          continue;
        }

        int ent_length = entries.length;
        for (int j = 0; j < ent_length; j++) {
          if (entries[j].getParent().equals(portletEntryName)) {
            JetspeedLink jsLink = JetspeedLinkFactory.getInstance(rundata);

            DynamicURI duri =
              jsLink.getLink(
                JetspeedLink.CURRENT,
                null,
                null,
                JetspeedLink.CURRENT,
                null);
            duri =
              duri
                .addPathInfo(
                  JetspeedResources.PATH_PANEID_KEY,
                  portletList[i].getId() + "," + entries[j].getId())
                .addQueryData(
                  JetspeedResources.PATH_ACTION_KEY,
                  "controls.Restore");
            return duri.toString();
          }
        }
      }
    } catch (Exception ex) {
      logger.error("Exception", ex);
      return null;
    }
    return null;
  }

  /**
   * ユーザ毎のルート保存先（絶対パス）を取得します。
   * 
   * @param uid
   * @return
   */
  public static String getSaveDirPath(String orgId, int uid) {
    return ALStorageService.getDocumentPath(FOLDER_FILEDIR_BLOG, CATEGORY_KEY
      + ALStorageService.separator()
      + uid);
  }

  /**
   * ユーザ毎の保存先（相対パス）を取得します。
   * 
   * @param uid
   * @return
   */
  public static String getRelativePath(String fileName) {
    return new StringBuffer().append("/").append(fileName).toString();
  }

  /**
   * 添付ファイルを取得します。
   * 
   * @param uid
   * @return
   */
  public static ArrayList<FileuploadLiteBean> getFileuploadList(RunData rundata) {
    String[] fileids =
      rundata
        .getParameters()
        .getStrings(FileuploadUtils.KEY_FILEUPLOAD_ID_LIST);
    if (fileids == null) {
      return null;
    }

    ArrayList<String> hadfileids = new ArrayList<String>();
    ArrayList<String> newfileids = new ArrayList<String>();

    for (int j = 0; j < fileids.length; j++) {
      if (fileids[j].trim().startsWith("s")) {
        hadfileids.add(fileids[j].trim().substring(1));
      } else {
        newfileids.add(fileids[j].trim());
      }
    }

    ArrayList<FileuploadLiteBean> fileNameList =
      new ArrayList<FileuploadLiteBean>();
    FileuploadLiteBean filebean = null;
    int fileid = 0;

    // 新規にアップロードされたファイルの処理
    if (newfileids.size() > 0) {
      String folderName =
        rundata.getParameters().getString(
          FileuploadUtils.KEY_FILEUPLOAD_FODLER_NAME);
      if (folderName == null || folderName.equals("")) {
        return null;
      }

      int length = newfileids.size();
      for (int i = 0; i < length; i++) {
        if (newfileids.get(i) == null || newfileids.get(i).equals("")) {
          continue;
        }

        try {
          fileid = Integer.parseInt(newfileids.get(i));
        } catch (Exception e) {
          continue;
        }

        if (fileid == 0) {
          filebean = new FileuploadLiteBean();
          filebean.initField();
          filebean.setFolderName("photo");
          filebean.setFileName("以前の写真ファイル");
          fileNameList.add(filebean);
        } else {
          BufferedReader reader = null;
          try {
            reader =
              new BufferedReader(new InputStreamReader(ALStorageService
                .getTmpFile(ALEipUtils.getUserId(rundata), folderName, fileid
                  + FileuploadUtils.EXT_FILENAME), FILE_ENCODING));
            String line = reader.readLine();
            if (line == null || line.length() <= 0) {
              continue;
            }

            filebean = new FileuploadLiteBean();
            filebean.initField();
            filebean.setFolderName(fileids[i]);
            filebean.setFileId(fileid);
            filebean.setFileName(line);
            fileNameList.add(filebean);
          } catch (Exception e) {
            logger.error("Exception", e);
          } finally {
            try {
              reader.close();
            } catch (Exception e) {
              logger.error("Exception", e);
            }
          }
        }

      }
    }

    if (hadfileids.size() > 0) {
      // すでにあるファイルの処理
      ArrayList<Integer> hadfileidsValue = new ArrayList<Integer>();
      for (int k = 0; k < hadfileids.size(); k++) {
        try {
          fileid = Integer.parseInt(hadfileids.get(k));
          hadfileidsValue.add(fileid);
        } catch (Exception e) {
          continue;
        }
      }

      try {
        SelectQuery<EipTBlogFile> reqquery = Database.query(EipTBlogFile.class);
        Expression reqexp1 =
          ExpressionFactory.inDbExp(
            EipTBlogFile.FILE_ID_PK_COLUMN,
            hadfileidsValue);
        reqquery.setQualifier(reqexp1);
        List<EipTBlogFile> requests = reqquery.fetchList();
        int requestssize = requests.size();
        for (int i = 0; i < requestssize; i++) {
          EipTBlogFile file = requests.get(i);
          filebean = new FileuploadBean();
          filebean.initField();
          filebean.setFileId(file.getFileId());
          filebean.setFileName(file.getTitle());
          filebean.setFlagNewFile(false);
          fileNameList.add(filebean);
        }
      } catch (Exception ex) {
        logger.error("[BlogUtils] Exception.", ex);
      }
    }
    return fileNameList;
  }

  /**
   * ファイルオブジェクトモデルを取得します。 <BR>
   * 
   * @param rundata
   * @param context
   * @return
   */
  public static List<EipTBlogFile> getEipTBlogFileList(int entryId) {
    try {
      SelectQuery<EipTBlogFile> query = Database.query(EipTBlogFile.class);
      Expression exp =
        ExpressionFactory.matchExp(
          EipTBlogFile.EIP_TBLOG_ENTRY_PROPERTY,
          Integer.valueOf(entryId));
      query.setQualifier(exp);
      List<EipTBlogFile> files = query.fetchList();
      if (files == null || files.size() == 0) {
        return null;
      }

      return files;
    } catch (Exception ex) {
      logger.error("Exception", ex);
      return null;
    }
  }

  public static boolean hasMinimumAuthority(RunData rundata) {
    ALAccessControlFactoryService aclservice =
      (ALAccessControlFactoryService) ((TurbineServices) TurbineServices
        .getInstance()).getService(ALAccessControlFactoryService.SERVICE_NAME);
    ALAccessControlHandler aclhandler = aclservice.getAccessControlHandler();

    boolean hasAuthority =
      aclhandler.hasAuthority(
        ALEipUtils.getUserId(rundata),
        ALAccessControlConstants.POERTLET_FEATURE_BLOG_ENTRY_SELF,
        ALAccessControlConstants.VALUE_ACL_LIST);

    if (!hasAuthority) {
      ALEipUtils.redirectPermissionError(rundata);
      return false;
    }
    return true;
  }

  public static void createNewBlogActivity(EipTBlogEntry blog, String loginName) {
    String title =
      new StringBuilder("ブログ「")
        .append(ALCommonUtils.compressString(blog.getTitle(), 30))
        .append("」を書きました。")
        .toString();
    String portletParams =
      new StringBuilder("?template=BlogDetailScreen")
        .append("&entityid=")
        .append(blog.getEntryId())
        .toString();
    ALActivityService.create(new ALActivityPutRequest()
      .withAppId("blog")
      .withLoginName(loginName)
      .withPortletParams(portletParams)
      .withTile(title)
      .witchPriority(0f)
      .withExternalId(String.valueOf(blog.getEntryId())));
  }

  public static void createNewCommentActivity(EipTBlogEntry blog,
      String loginName, String targetLoginName) {
    if (loginName.equals(targetLoginName)) {
      return;
    }
    List<String> recipients = new ArrayList<String>();
    recipients.add(targetLoginName);
    String title =
      new StringBuilder("ブログ「")
        .append(ALCommonUtils.compressString(blog.getTitle(), 30))
        .append("」にコメントしました。")
        .toString();
    String portletParams =
      new StringBuilder("?template=BlogDetailScreen")
        .append("&entityid=")
        .append(blog.getEntryId())
        .toString();
    ALActivityService.create(new ALActivityPutRequest()
      .withAppId("Blog")
      .withLoginName(loginName)
      .withPortletParams(portletParams)
      .withRecipients(recipients)
      .withTile(title)
      .witchPriority(1f)
      .withExternalId(String.valueOf(blog.getEntryId())));
  }

  /**
   * 検索クエリ用の所有者IDを取得します。
   * 
   * @param rundata
   * @param context
   * @return
   */
  public static String getOwnerId(RunData rundata, Context context) {
    String ownerId = null;
    String ownerIdParam = rundata.getParameters().getString(OWNER_ID);
    ownerId = ALEipUtils.getTemp(rundata, context, OWNER_ID);

    if (ownerIdParam == null && (ownerId == null)) {
      ALEipUtils.setTemp(rundata, context, OWNER_ID, "all");
      ownerId = "all";
    } else if (ownerIdParam != null) {
      ALEipUtils.setTemp(rundata, context, OWNER_ID, ownerIdParam);
      ownerId = ownerIdParam;
      // 検索キーワードをクリア
      ALEipUtils.setTemp(rundata, context, SEARCH_WORD, "");
    }
    return ownerId;
  }

  /**
   * 検索クエリ用のキーワードを取得します。
   * 
   * @param rundata
   * @param context
   * @return
   */
  public static String getKeyword(RunData rundata, Context context) {
    String keyword = null;
    String keywordParm = rundata.getParameters().getString(SEARCH_WORD);
    keyword = ALEipUtils.getTemp(rundata, context, SEARCH_WORD);
    if (keywordParm == null && (keyword == null)) {
      ALEipUtils.setTemp(rundata, context, SEARCH_WORD, "");
      keyword = "";
    } else if (keywordParm != null) {
      keywordParm.trim();
      ALEipUtils.setTemp(rundata, context, SEARCH_WORD, keywordParm);
      keyword = keywordParm;
    }
    return keyword;
  }

  /**
   * 検索クエリ用のテーマIDを取得します。
   * 
   * @param rundata
   * @param context
   * @return
   */
  public static String getThemeId(RunData rundata, Context context) {
    String themeId = null;
    String themeIdParam = rundata.getParameters().getString(THEME_ID);
    themeId = ALEipUtils.getTemp(rundata, context, THEME_ID);
    if (themeIdParam == null && (themeId == null)) {
      ALEipUtils.setTemp(rundata, context, THEME_ID, "all");
      themeId = "all";
    } else if (themeIdParam != null) {
      themeIdParam.trim();
      ALEipUtils.setTemp(rundata, context, THEME_ID, themeIdParam);
      themeId = themeIdParam;
      // 検索キーワードをクリア
      ALEipUtils.setTemp(rundata, context, SEARCH_WORD, "");
    }
    return themeId;
  }

  /**
   * 表示されているグループIDを取得します。
   * 
   * @param rundata
   * @param context
   * @return
   */
  public static String getGroupId(RunData rundata, Context context) {
    String groupId = null;
    String groupIdParam = rundata.getParameters().getString(GROUP_ID);
    groupId = ALEipUtils.getTemp(rundata, context, GROUP_ID);
    if (groupIdParam == null && (groupId == null)) {
      ALEipUtils.setTemp(rundata, context, GROUP_ID, "LoginUser");
      groupId = "LoginUser";
    } else if (groupIdParam != null) {
      groupIdParam.trim();
      ALEipUtils.setTemp(rundata, context, GROUP_ID, groupIdParam);
      groupId = groupIdParam;
    }
    return groupId;
  }

  /**
   * ブログ固有のフィルタをクエリに適用します
   * 
   * @param query
   * @param rundata
   * @param context
   * @return
   */
  public static SelectQuery<EipTBlogEntry> buildSelectQueryForBlogFilter(
      SelectQuery<EipTBlogEntry> query, RunData rundata, Context context) {

    // 所有者
    String ownerId = BlogUtils.getOwnerId(rundata, context);
    if (!ownerId.equals("all")) {
      Expression exp =
        ExpressionFactory.matchDbExp(EipTBlogEntry.OWNER_ID_COLUMN, ownerId);
      query.andQualifier(exp);
    }

    // テーマ
    String themeId = BlogUtils.getThemeId(rundata, context);
    if (!themeId.equals("all")) {
      Expression exp =
        ExpressionFactory.matchExp(
          EipTBlogEntry.EIP_TBLOG_THEMA_PROPERTY,
          themeId);
      query.andQualifier(exp);
    }

    // 検索キーワード
    String queryKeyword = BlogUtils.getKeyword(rundata, context);
    String[] keywords = queryKeyword.split("[ 　]");
    for (int i = 0; i < keywords.length; i++) {
      String keyword = keywords[i];
      if (keyword.length() > 0) {
        String keywordExp = MessageFormat.format("%{0}%", keyword);
        Expression exp1 =
          ExpressionFactory.likeExp(EipTBlogEntry.TITLE_PROPERTY, keywordExp);
        Expression exp2 =
          ExpressionFactory.likeExp(EipTBlogEntry.NOTE_PROPERTY, keywordExp);
        Expression exp = exp1.orExp(exp2);
        query.andQualifier(exp);
      }
    }
    return query;
  }

  /**
   * アクセス権限をチェックします。
   * 
   * @return
   */
  public static boolean checkPermission(RunData rundata, Context context,
      int defineAclType, String pfeature) {

    if (defineAclType == 0) {
      return true;
    }

    if (pfeature == null || "".equals(pfeature)) {
      return true;
    }

    ALAccessControlFactoryService aclservice =
      (ALAccessControlFactoryService) ((TurbineServices) TurbineServices
        .getInstance()).getService(ALAccessControlFactoryService.SERVICE_NAME);
    ALAccessControlHandler aclhandler = aclservice.getAccessControlHandler();
    boolean hasAuthority =
      aclhandler.hasAuthority(
        ALEipUtils.getUserId(rundata),
        pfeature,
        defineAclType);

    return hasAuthority;
  }

  public static int getViewId(RunData rundata, Context context, int uid)
      throws ALDBErrorException {
    int view_uid = -1;
    EipTBlogEntry record = BlogUtils.getEipTBlogEntry(rundata, context);
    if (record != null) {
      view_uid = record.getOwnerId();
    } else {
      if (rundata.getParameters().containsKey("view_uid")) {
        view_uid =
          Integer.parseInt(rundata.getParameters().getString("view_uid"));
      } else {
        view_uid = uid;
      }
    }
    ALEipUtils.setTemp(rundata, context, "view_uid", String.valueOf(view_uid));
    return view_uid;
  }

  public static int getCommentViewId(RunData rundata, Context context, int uid,
      String commentid) throws ALDBErrorException, ALPageNotFoundException {
    int view_uid = -1;
    EipTBlogComment record =
      BlogUtils.getEipTBlogComment(rundata, context, commentid);
    if (record != null) {
      view_uid = record.getOwnerId();
    } else {
      if (rundata.getParameters().containsKey("comment_view_uid")) {
        view_uid =
          Integer.parseInt(rundata
            .getParameters()
            .getString("comment_view_uid"));
      } else {
        view_uid = uid;
      }
    }
    ALEipUtils.setTemp(rundata, context, "comment_view_uid", String
      .valueOf(view_uid));
    return view_uid;
  }
}