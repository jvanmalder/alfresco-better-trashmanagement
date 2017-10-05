/*
 * Copyright 2017 Axel Faust
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.axelfaust.alfresco.trash.management.repo.web.scripts;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.node.archive.NodeArchiveService;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.repository.datatype.DefaultTypeConverter;
import org.alfresco.service.cmr.search.QueryConsistency;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.cmr.search.SearchParameters;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.PropertyCheck;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.DeclarativeWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;

/**
 * @author Axel Faust, <a href="http://acosix.de">Acosix GmbH</a>
 */
public class ArchivedItemsQueryGet extends DeclarativeWebScript implements InitializingBean
{

    protected NamespaceService namespaceService;

    protected NodeService nodeService;

    protected NodeArchiveService nodeArchiveService;

    protected PersonService personService;

    protected SearchService searchService;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "namespaceService", this.namespaceService);
        PropertyCheck.mandatory(this, "nodeService", this.nodeService);
        PropertyCheck.mandatory(this, "nodeArchiveService", this.nodeArchiveService);
        PropertyCheck.mandatory(this, "personService", this.personService);
        PropertyCheck.mandatory(this, "searchService", this.searchService);
    }

    /**
     * @param namespaceService
     *            the namespaceService to set
     */
    public void setNamespaceService(final NamespaceService namespaceService)
    {
        this.namespaceService = namespaceService;
    }

    /**
     * @param nodeService
     *            the nodeService to set
     */
    public void setNodeService(final NodeService nodeService)
    {
        this.nodeService = nodeService;
    }

    /**
     * @param nodeArchiveService
     *            the nodeArchiveService to set
     */
    public void setNodeArchiveService(final NodeArchiveService nodeArchiveService)
    {
        this.nodeArchiveService = nodeArchiveService;
    }

    /**
     * @param personService
     *            the personService to set
     */
    public void setPersonService(final PersonService personService)
    {
        this.personService = personService;
    }

    /**
     * @param searchService
     *            the searchService to set
     */
    public void setSearchService(final SearchService searchService)
    {
        this.searchService = searchService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Map<String, Object> executeImpl(final WebScriptRequest req, final Status status, final Cache cache)
    {
        final Map<String, Object> model = new HashMap<>();

        final String baseStoreParam = req.getParameter("baseStore");

        final String startIndexParam = req.getParameter("startIndex");
        final String pageParam = req.getParameter("page");
        final String pageSizeParam = req.getParameter("pageSize");

        final StoreRef baseStore = baseStoreParam != null && !baseStoreParam.trim().isEmpty() ? new StoreRef(baseStoreParam)
                : StoreRef.STORE_REF_WORKSPACE_SPACESSTORE;

        final int pageSize = pageSizeParam != null && !pageSizeParam.trim().isEmpty() ? Integer.parseInt(pageSizeParam, 10) : 50;
        int startIndex = startIndexParam != null && !startIndexParam.trim().isEmpty() ? Integer.parseInt(startIndexParam, 10) : -1;
        if (startIndex < 0 && pageParam != null)
        {
            final int page = Integer.parseInt(pageParam, 10);
            startIndex = page * pageSize + 1;
        }

        if (startIndex < 0)
        {
            startIndex = 0;
        }

        final NodeRef storeArchiveNode = this.nodeArchiveService.getStoreArchiveNode(baseStore);
        final Map<String, Object> paginationModel = new HashMap<>();
        model.put("pagination", paginationModel);

        paginationModel.put("startIndex", Integer.valueOf(startIndex));
        paginationModel.put("totalRecords", Integer.valueOf(0));
        paginationModel.put("numberFound", Integer.valueOf(0));

        if (storeArchiveNode != null)
        {
            final SearchParameters sp = this.prepareSearchParameters(req, storeArchiveNode, pageSize, startIndex);

            final ResultSet resultSet = this.searchService.query(sp);
            try
            {
                final List<Map<String, Object>> results = this.processResults(resultSet);
                model.put("results", results);
                paginationModel.put("totalRecords", Integer.valueOf(resultSet.length()));
                paginationModel.put("numberFound", Long.valueOf(resultSet.getNumberFound()));
            }
            finally
            {
                resultSet.close();
            }
        }
        else
        {
            model.put("results", new ArrayList<Map<String, Object>>());
        }

        return model;
    }

    protected SearchParameters prepareSearchParameters(final WebScriptRequest req, final NodeRef storeArchiveNode, final int pageSize,
            final int startIndex)
    {
        final String archivedByUserParam = req.getParameter("archivedByUser");
        final String topLevelParam = req.getParameter("topLevel");
        final String archivedByUser = archivedByUserParam != null && !archivedByUserParam.trim().isEmpty() ? archivedByUserParam : null;
        final boolean topLevel = topLevelParam != null && !topLevelParam.trim().isEmpty() ? Boolean.parseBoolean(topLevelParam) : true;

        final String filterQueryParam = req.getParameter("filterQuery");
        final String nameForSearchParam = req.getParameter("name");

        final SearchParameters sp = new SearchParameters();
        sp.addStore(storeArchiveNode.getStoreRef());
        sp.setLanguage(SearchService.LANGUAGE_FTS_ALFRESCO);
        // TODO Switch to TRANSACTIONAL_IF_POSSIBLE when we can handle paginated topLevel queries for large archived elements counts in
        // an efficient manner
        sp.setQueryConsistency(QueryConsistency.EVENTUAL);

        final StringBuilder queryBuilder = new StringBuilder();
        if (topLevel)
        {
            if (archivedByUser != null)
            {
                queryBuilder.append('=').append(ContentModel.PROP_ARCHIVED_BY.toPrefixString(this.namespaceService)).append(":\"")
                        .append(archivedByUser).append('"').append(" AND ");
            }
            queryBuilder.append("ASPECT:\"").append(ContentModel.ASPECT_ARCHIVED.toPrefixString(this.namespaceService)).append('"');
        }
        else if (archivedByUser != null)
        {
            final NodeRef archiveUserNode = AuthenticationUtil.runAsSystem(() -> {
                final List<ChildAssociationRef> archiveUserAssocs = this.nodeService.getChildrenByName(storeArchiveNode,
                        ContentModel.ASSOC_ARCHIVE_USER_LINK, Collections.singletonList(archivedByUser));
                final NodeRef archiveUser = archiveUserAssocs.isEmpty() ? archiveUserAssocs.get(0).getChildRef() : null;
                return archiveUser;
            });
            queryBuilder.append("ANCESTOR:\"").append(archiveUserNode).append('"');
        }
        else
        {
            queryBuilder.append("NOT TYPE:\"").append(ContentModel.TYPE_ARCHIVE_USER.toPrefixString(this.namespaceService)).append('"');
        }
        sp.setQuery(queryBuilder.toString());

        if (nameForSearchParam != null && !nameForSearchParam.trim().isEmpty())
        {
            queryBuilder.delete(0, queryBuilder.length());
            queryBuilder.append('=').append(ContentModel.PROP_NAME.toPrefixString(this.namespaceService)).append(":\"")
                    .append(nameForSearchParam).append('"');
            sp.addFilterQuery(queryBuilder.toString());
        }

        if (filterQueryParam != null && !filterQueryParam.trim().isEmpty())
        {
            sp.addFilterQuery(filterQueryParam);
        }

        sp.setSkipCount(startIndex);
        sp.setLimit(pageSize);
        sp.setMaxItems(pageSize);
        return sp;
    }

    protected List<Map<String, Object>> processResults(final ResultSet resultSet)
    {
        final List<Map<String, Object>> results = new ArrayList<>();
        final Map<String, Map<String, Object>> userObjByUserName = new HashMap<>();

        resultSet.getNodeRefs().forEach((result) -> {
            final Map<QName, Serializable> resultProperties = this.nodeService.getProperties(result);

            final String modifier = DefaultTypeConverter.INSTANCE.convert(String.class, resultProperties.get(ContentModel.PROP_MODIFIER));
            String archiver = DefaultTypeConverter.INSTANCE.convert(String.class, resultProperties.get(ContentModel.PROP_ARCHIVED_BY));
            Date archivedOn = DefaultTypeConverter.INSTANCE.convert(Date.class, resultProperties.get(ContentModel.PROP_ARCHIVED_DATE));

            final NodeRef previousNode = result;
            while (archiver == null)
            {
                final ChildAssociationRef primaryParent = this.nodeService.getPrimaryParent(previousNode);
                if (primaryParent == null)
                {
                    break;
                }
                final NodeRef parentRef = primaryParent.getParentRef();
                if (this.nodeService.hasAspect(parentRef, ContentModel.ASPECT_ARCHIVED))
                {
                    final Map<QName, Serializable> archivedItemProperties = this.nodeService.getProperties(parentRef);
                    archiver = DefaultTypeConverter.INSTANCE.convert(String.class,
                            archivedItemProperties.get(ContentModel.PROP_ARCHIVED_BY));
                    archivedOn = DefaultTypeConverter.INSTANCE.convert(Date.class,
                            archivedItemProperties.get(ContentModel.PROP_ARCHIVED_DATE));
                }
            }

            Map<String, Object> modifierObj = userObjByUserName.get(modifier);
            if (modifierObj == null)
            {
                modifierObj = this.buildUserObject(modifier);
                userObjByUserName.put(modifier, modifierObj);
            }

            Map<String, Object> archiverObj = userObjByUserName.get(archiver);
            if (archiverObj == null)
            {
                archiverObj = this.buildUserObject(modifier);
                userObjByUserName.put(archiver, archiverObj);
            }

            final Map<String, Object> itemObj = new HashMap<>();
            itemObj.put("modifier", modifierObj);
            itemObj.put("archiver", archiverObj);
            itemObj.put("archivedOn", archivedOn);
            itemObj.put("node", result);
            results.add(itemObj);
        });
        return results;
    }

    private Map<String, Object> buildUserObject(final String modifier)
    {
        Map<String, Object> modifierObj;
        modifierObj = new HashMap<>();
        modifierObj.put("userName", modifier);
        final NodeRef person = this.personService.getPerson(modifier, false);
        if (person != null)
        {
            final Map<QName, Serializable> personProperties = this.nodeService.getProperties(person);
            final String firstName = DefaultTypeConverter.INSTANCE.convert(String.class, personProperties.get(ContentModel.PROP_FIRSTNAME));
            final String lastName = DefaultTypeConverter.INSTANCE.convert(String.class, personProperties.get(ContentModel.PROP_LASTNAME));

            modifierObj.put("firstName", firstName != null ? firstName.trim() : "");
            modifierObj.put("lastName", lastName != null ? lastName.trim() : "");
            final StringBuilder displayNameBuilder = new StringBuilder();
            if (firstName != null && !firstName.trim().isEmpty())
            {
                displayNameBuilder.append(firstName.trim());
            }
            if (lastName != null && !lastName.trim().isEmpty())
            {
                if (firstName != null && !firstName.trim().isEmpty())
                {
                    displayNameBuilder.append(' ');
                }
                displayNameBuilder.append(lastName.trim());
            }
            if (displayNameBuilder.length() == 0)
            {
                displayNameBuilder.append(modifier);
            }
            modifierObj.put("displayName", displayNameBuilder.toString());
        }
        else if (modifier.matches("^" + AuthenticationUtil.getSystemUserName() + "(@.+)?$"))
        {
            modifierObj.put("firstName", "System");
            modifierObj.put("lastName", "User");
            modifierObj.put("displayName", "System User");
        }
        else
        {
            modifierObj.put("displayName", modifier);
        }
        return modifierObj;
    }

}
