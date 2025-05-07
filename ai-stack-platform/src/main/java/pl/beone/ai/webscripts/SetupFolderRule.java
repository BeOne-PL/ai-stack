package pl.beone.ai.webscripts;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.action.evaluator.IsSubTypeEvaluator;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.action.Action;
import org.alfresco.service.cmr.action.ActionCondition;
import org.alfresco.service.cmr.action.ActionService;
import org.alfresco.service.cmr.action.CompositeAction;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.rule.Rule;
import org.alfresco.service.cmr.rule.RuleService;
import org.alfresco.service.cmr.rule.RuleType;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.DeclarativeWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Web Script responsible for applying folder-level rules in Alfresco.
 * <p>
 * Depending on the aspect passed as a parameter, it either:
 * <ul>
 *   <li>Creates a synchronization rule that sets {@code cm:updated} to a fixed date</li>
 *   <li>Creates a feature-enabling rule that adds a given aspect to subfolders</li>
 * </ul>
 * This script is intended to automate folder behavior setup, e.g. for synchronization pipelines.
 */

@Slf4j
public class SetupFolderRule extends DeclarativeWebScript {
    @Autowired
    ServiceRegistry serviceRegistry;

    @Autowired
    RuleService ruleService;

    @Autowired
    ActionService actionService;

    @Autowired
    NodeService nodeService;

    @Value("${alfresco.ai.sync.aspect:cm:syndication}")
    private String syncAspect;

    private final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    /**
     * Handles the execution of the Web Script request to set up folder-level rules.
     * <p>
     * Depending on the passed aspect, creates either a sync rule or a feature-enabling rule.
     * The result is returned in the response model.
     * </p>
     *
     * @param req the web script request
     * @param status the response status handler
     * @param cache the caching handler
     * @return a map containing the operation result
     */
    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache) {
        Map<String, Object> model = new HashMap<>();
        try {
            String nodeId = req.getParameter("nodeId");
            if (StringUtils.isBlank(nodeId)) {
                throw new IllegalArgumentException("Missing 'nodeId' parameter");
            }

            String aspectId = req.getParameter("aspectId");
            if (StringUtils.isBlank(aspectId)) {
                throw new IllegalArgumentException("Missing 'aspectId' parameter");
            }

            NodeRef folderNodeRef = new NodeRef("workspace://SpacesStore/" + nodeId);
            if (!nodeService.exists(folderNodeRef)) {
                throw new IllegalArgumentException("Node does not exist: " + folderNodeRef);
            }

            if(StringUtils.equals(aspectId, syncAspect)) {
                if (hasSyncRule(folderNodeRef)) {
                    model.put("result", "Rule already exists. Skipping creation.");
                } else {
                    createSyncRule(folderNodeRef);
                    model.put("result", "Rule created successfully.");
                }
            } else {
                // default to addFeatures
                if(hasAddFeaturesRule(folderNodeRef, aspectId)) {
                    model.put("result", "Rule already exists. Skipping creation.");
                } else {
                    addFeatures(folderNodeRef, aspectId);
                    model.put("result", "Rule created successfully.");
                }
            }


        } catch (Exception e) {
            log.error("Error setting up folder rule", e);
            model.put("result", "Error: " + e.getMessage());
        }

        return model;
    }

    /**
     * Checks whether a rule already exists on the given folder that adds a specific aspect to inbound subfolders.
     *
     * @param folderRef the node reference of the folder to check
     * @param aspectId the aspect (QName) that should be applied by the rule
     * @return {@code true} if such a rule exists, {@code false} otherwise
     */
    private boolean hasAddFeaturesRule(NodeRef folderRef, String aspectId) {
        List<Rule> rules = ruleService.getRules(folderRef);
        for (Rule rule : rules) {
            if (StringUtils.contains(rule.getTitle(), aspectId) && rule.getRuleTypes().contains(RuleType.INBOUND)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether a synchronization rule ("Sync Folder") already exists on the given folder.
     *
     * @param folderRef the node reference of the folder to check
     * @return {@code true} if the sync rule exists, {@code false} otherwise
     */
    private boolean hasSyncRule(NodeRef folderRef) {
        List<Rule> rules = ruleService.getRules(folderRef);
        for (Rule rule : rules) {
            if ("Sync Folder".equals(rule.getTitle()) && rule.getRuleTypes().contains(RuleType.INBOUND)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds a rule to the specified folder that assigns a given aspect to all inbound subfolders.
     *
     * @param folderNodeRef the folder node to which the rule will be applied
     * @param aspectId the qualified name of the aspect to assign
     * @throws Exception if rule creation or saving fails
     */
    public void addFeatures(NodeRef folderNodeRef, String aspectId) throws Exception {
        CompositeAction compositeAction = actionService.createCompositeAction();

        ActionCondition condition = actionService.createActionCondition(IsSubTypeEvaluator.NAME);
        condition.setInvertCondition(false);
        Map<String, Serializable> conditionParameters = new HashMap<>(1);
        conditionParameters.put(IsSubTypeEvaluator.PARAM_TYPE, ContentModel.TYPE_FOLDER);
        condition.setParameterValues(conditionParameters);
        compositeAction.addActionCondition(condition);

        Action action = actionService.createAction("add-features");
        action.setExecuteAsynchronously(false);
        action.setParameterValue("aspect-name", QName.createQName(aspectId, serviceRegistry.getNamespaceService()));
        action.setParameterValue("actionContext", "rule");
        compositeAction.addAction(action);

        Rule rule = new Rule();
        rule.setRuleType(RuleType.INBOUND);
        rule.setTitle("Folder add features rule: " + aspectId);
        rule.setDescription("Sets " + aspectId + " aspect on child folders to enable its features.");
        rule.setExecuteAsynchronously(false);
        rule.applyToChildren(true);
        rule.setRuleDisabled(false);
        rule.setAction(compositeAction);

        ruleService.saveRule(folderNodeRef, rule);
        ruleService.enableRules(folderNodeRef);
    }

    /**
     * Creates a synchronization rule on the specified folder.
     * <p>
     * The rule applies to inbound folders and sets the {@code cm:updated} property
     * to a fixed timestamp, enabling sync-based tracking.
     * </p>
     *
     * @param folderNodeRef the target folder node for which the sync rule is applied
     * @throws Exception if rule creation or saving fails
     */
    public void createSyncRule(NodeRef folderNodeRef) throws Exception {
        CompositeAction compositeAction = actionService.createCompositeAction();

        ActionCondition condition = actionService.createActionCondition(IsSubTypeEvaluator.NAME);
        condition.setInvertCondition(false);
        Map<String, Serializable> conditionParameters = new HashMap<>(1);
        conditionParameters.put(IsSubTypeEvaluator.PARAM_TYPE, ContentModel.TYPE_FOLDER);
        condition.setParameterValues(conditionParameters);
        compositeAction.addActionCondition(condition);

        Action action = actionService.createAction("set-property-value");
        action.setExecuteAsynchronously(false);
        QName cmUpdated = QName.createQName(org.alfresco.model.ContentModel.PROP_UPDATED.getNamespaceURI(), "updated");
        action.setParameterValue("property", cmUpdated);
        action.setParameterValue("prop_type", "d:datetime");
        Date fixedDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
                .parse("1999-01-01T00:00:00.000+01:00");
        action.setParameterValue("value", fixedDate);
        action.setParameterValue("actionContext", "rule");
        compositeAction.addAction(action);

        Rule rule = new Rule();
        rule.setRuleType(RuleType.INBOUND);
        rule.setTitle("Sync Folder");
        rule.setDescription("Sets cm:updated on folders to fixed date");
        rule.setExecuteAsynchronously(false);
        rule.applyToChildren(true);
        rule.setRuleDisabled(false);
        rule.setAction(compositeAction);

        ruleService.saveRule(folderNodeRef, rule);
        ruleService.enableRules(folderNodeRef);
    }

}
