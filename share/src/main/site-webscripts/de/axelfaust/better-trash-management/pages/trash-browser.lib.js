/* exported augmentServices */
function augmentServices(services)
{
    var idx, requiredServices, service;

    // track services we need to add (currently none of the services need specific configuration options)
    requiredServices = {
        'better-trash-management/services/TrashManagementService' : true,
        'alfresco/services/NodePreviewService' : true,
        'alfresco/services/LightboxService' : true,
        'alfresco/services/DialogService' : true,
        // needed for some lazy loading of data (some service expect data in weird/complex structures that we don't provide
        // we would have the data, but there is no way to configure services / widgets to use our data, and transforming is out of the
        // question
        // example: alfresco/renderers/Thumbnail doing a hard-coded ALF_RETRIEVE_SINGLE_DOCUMENT_REQUEST if currentItem does not provide
        // type/mimetype via hard-coded object paths
        'alfresco/services/DocumentService' : true
    };

    for (idx = 0; idx < services.length; idx++)
    {
        if (typeof services[idx].name === 'string' && requiredServices.hasOwnProperty(services[idx].name))
        {
            requiredServices[services[idx].name] = false;
        }
        else if (typeof services[idx] === 'string' && requiredServices.hasOwnProperty(services[idx]))
        {
            requiredServices[services[idx]] = false;
        }
    }

    for (service in requiredServices)
    {
        if (requiredServices.hasOwnProperty(service))
        {
            if (requiredServices[service] === true)
            {
                services.push({
                    name : service,
                    config : {}
                });
            }
        }
    }
}

/* exported buildMainPanel */
function buildMainPanel()
{
    var model;

    model = {
        name : 'alfresco/core/ProcessWidgets',
        config : {
            pubSubScope : 'TRASH_ITEM_LIST/',
            widgets : [
                    {
                        name : 'alfresco/lists/Paginator',
                        config : {
                            documentsPerPage : 20,
                            pageSizes : [ 20, 50, 100 ],
                            style : 'text-align:center;',
                            widgetsBefore : [ {
                                name : 'alfresco/documentlibrary/AlfSelectDocumentListItems'
                            } ],
                            widgetsAfter : [ {
                                name : 'alfresco/documentlibrary/AlfSelectedItemsMenuBarPopup',
                                config : {
                                    // can't believe "selected-items.label" is not a global label
                                    // we prefix it to not mess with any global labels others may have added
                                    // TODO Report bug / enhancement
                                    label : 'trash-browser.paginator.selected-items.label',
                                    passive : false,
                                    itemKeyProperty : 'nodeRef',
                                    widgets : [
                                    // TODO bulk actions
                                    ]
                                }
                            } ]
                        }
                    },
                    {
                        name : 'alfresco/lists/AlfFilteredList',
                        config : {
                            reloadDataTopic : 'RELOAD_TRASH_ITEMS',
                            loadDataPublishTopic : 'BETTER_TRASH_MANAGEMENT_QUERY_ARCHIVED_ITEMS',
                            loadDataPublishPayload : {
                                defaultOperator : 'AND',
                                defaultQueryTemplate : '%(cm:name cm:title cm:description ia:whatEvent ia:descriptionEvent lnk:title lnk:description TEXT TAG)'
                            // TODO Fill with whatever the service needs
                            },
                            // TODO Report enhancement - filtering should not require these form topic cludges
                            filteringTopics : [ '_valueChangeOf_ITEM_NAME', '_valueChangeOf_KEYWORDS', '_valueChangeOf_ARCHIVE_DATE',
                                    '_valueChangeOf_TOP_LEVEL' ],
                            widgetsForFilters : [ {
                                name : 'alfresco/forms/controls/TextBox',
                                config : {
                                    // TODO Report enhancement - filter widgets should align properly
                                    // TODO Report enhancement - simple width customisation
                                    style : 'vertical-align:top;',
                                    fieldId : 'ITEM_NAME',
                                    name : 'name',
                                    label : 'trash-browser.filter.itemName.label',
                                    placeHolder : 'trash-browser.filter.itemName.placeHolder'
                                }
                            }, {
                                name : 'alfresco/forms/controls/TextBox',
                                config : {
                                    // TODO Report enhancement - filter widgets should align properly
                                    // TODO Report enhancement - simple width customisation
                                    style : 'vertical-align:top;',
                                    fieldId : 'KEYWORDS',
                                    name : 'keywords',
                                    label : 'trash-browser.filter.keywords.label',
                                    placeHolder : 'trash-browser.filter.keywords.placeHolder'
                                }
                            }, {
                                name : 'better-trash-management/forms/controls/DateRange',
                                config : {
                                    // TODO Report enhancement - filter widgets should align properly
                                    // TODO Report enhancement - allow configuration option in base DateRange widget to allow open-ended ranges
                                    // TODO Report bug - change of "from" value does not trigger pubSub in base DateRange widget
                                    style : 'vertical-align:top;',
                                    fieldId : 'ARCHIVE_DATE',
                                    name : 'archiveDate',
                                    label : 'trash-browser.filter.archiveDate.label',
                                    allowOpenEndDateRange : true
                                }
                            }, {
                                name : 'alfresco/forms/controls/CheckBox',
                                config : {
                                    // TODO Report enhancement - filter widgets should align properly
                                    style : 'vertical-align:top;',
                                    fieldId : 'TOP_LEVEL',
                                    name : 'topLevel',
                                    label : 'trash-browser.filter.topLevel.label'
                                }
                            } ],
                            usePagination : true,
                            currentPageSize : 20,
                            itemsProperty : 'items', // TODO Set to whatever we get back from a service
                            widgets : [ {
                                name : 'alfresco/lists/views/AlfListView',
                                config : {
                                    additionalCssClasses : 'bordered',
                                    widgetsForHeader : [],
                                    widgets : [ {
                                        name : 'alfresco/lists/views/layouts/Row',
                                        config : {
                                            // TODO Report bug - property zebraStriping without effect
                                            additionalCssClasses : 'zebra-striping',
                                            // TODO Report enhancement - simple CellProperty widget
                                            // TODO Report enhancement - support of expanded (alternative) value as tooltip / mouseover
                                            widgets : [ {
                                                name : 'alfresco/lists/views/layouts/Cell',
                                                config : {
                                                    additionalCssClasses : 'smallpad',
                                                    widgets : [ {
                                                        name : 'alfresco/renderers/Selector',
                                                        itemKey : 'nodeRef',
                                                        publishGlobal : false,
                                                        publishToParent : false
                                                    } ]
                                                }
                                            }, {
                                                name : 'alfresco/lists/views/layouts/Cell',
                                                config : {
                                                    additionalCssClasses : 'smallpad',
                                                    widgets : [ {
                                                        name : 'alfresco/renderers/Thumbnail',
                                                        config : {
                                                            itemKey : 'nodeRef',
                                                            imageTitleProperty : 'name',
                                                            lastThumbnailModificationProperty : 'properties.cm:lastThumbnailModification',
                                                            renditionName : 'doclib',
                                                            showDocumentPreview : true,
                                                            usePreviewService : true,
                                                            publishGlobal : false,
                                                            publishToParent : false
                                                        }
                                                    } ]
                                                }
                                            }, {
                                                name : 'alfresco/lists/views/layouts/Cell',
                                                config : {
                                                    // TODO Expand cell into a document library-like details view
                                                    // TODO Consider how people might be able to customise view for different types of
                                                    // archived
                                                    // items
                                                    additionalCssClasses : 'smallpad',
                                                    widgets : [ {
                                                        name : 'alfresco/core/ProcessWidgets',
                                                        config : {
                                                            widgets : [ {
                                                                name : 'alfresco/renderers/Property',
                                                                config : {
                                                                    propertyToRender : 'properties.cm:name',
                                                                    renderSize : 'medium'
                                                                }
                                                            }, {
                                                                name : 'alfresco/renderers/Property',
                                                                config : {
                                                                    // TODO Define (dynamic) CSS class
                                                                    style : 'margin-left: 2ex;',
                                                                    propertyToRender : 'properties.cm:title',
                                                                    renderSize : 'medium',
                                                                    renderedValuePrefix : '(',
                                                                    renderedValueSuffix : ')',
                                                                    renderFilter : [ {
                                                                        property : 'properties.cm:title',
                                                                        renderOnAbsentProperty : false,
                                                                        values : [ '' ],
                                                                        negate : true
                                                                    } ]
                                                                }
                                                            }, {
                                                                name : "alfresco/renderers/Date",
                                                                config : {
                                                                    renderOnNewLine : true,
                                                                    modifiedDateProperty : 'modified',
                                                                    modifiedByProperty : 'modifierDisplayName'
                                                                }
                                                            }, {
                                                                name : "alfresco/renderers/Date",
                                                                config : {
                                                                    renderOnNewLine : true,
                                                                    modifiedDateProperty : 'archived',
                                                                    modifiedByProperty : 'archiverDisplayName',
                                                                    modifiedByMessage : 'details.archived-by'
                                                                }
                                                            } ]
                                                        }
                                                    } ]
                                                }
                                            }, {
                                                name : 'alfresco/lists/views/layouts/Cell',
                                                config : {
                                                    widgets : [ {
                                                        name : 'alfresco/renderers/Actions',
                                                        config : {
                                                            // TODO Report enhancement - make size of Actions configurable (it is frigging
                                                            // huge)
                                                            // TODO Report enhancement - Actions should allow providing custom actions like
                                                            // the
                                                            // pre-defined action widgets (currently customActions are mapped into
                                                            // AlfMenuItem
                                                            // dropping some options and adding some non-suppressable ones)
                                                            customActions : []
                                                        }
                                                    } ]
                                                }
                                            } ]
                                        }
                                    } ]
                                }
                            } ],
                            // TODO Report enhancement - layout of AlfList (and sub-modules) should allow for consistent padding to stop
                            // views
                            // clinging to the edge while not forcing i.e. filter form to have an even larger inset than by default
                            style : 'padding: 0 10px;'
                        }
                    } ]
        }
    };

    return model;
}
