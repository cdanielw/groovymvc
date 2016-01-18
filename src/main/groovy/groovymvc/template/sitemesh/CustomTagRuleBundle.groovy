package groovymvc.template.sitemesh

import org.sitemesh.SiteMeshContext
import org.sitemesh.content.ContentProperty
import org.sitemesh.content.tagrules.TagRuleBundle
import org.sitemesh.content.tagrules.decorate.SiteMeshDecorateRule
import org.sitemesh.tagprocessor.*
/**
 * @author Daniel Wiell
 */
class CustomTagRuleBundle implements TagRuleBundle {
    private final List<String> customTags

    CustomTagRuleBundle(List<String> customTags) {
        this.customTags = customTags
    }

    void install(State defaultState, ContentProperty contentProperty, SiteMeshContext siteMeshContext) {
        customTags.each {
            defaultState.addRule(it, new CustomTagRule(contentProperty.getChild(it), siteMeshContext))
        }
    }

    void cleanUp(State defaultState, ContentProperty contentProperty, SiteMeshContext siteMeshContext) {}

    private static class CustomTagRule extends BasicBlockRule {
        private final ContentProperty propertyToExport
        private final SiteMeshDecorateRule decorateRule

        public CustomTagRule(ContentProperty propertyToExport, SiteMeshContext siteMeshContext) {
            this.propertyToExport = propertyToExport
            decorateRule = new SiteMeshDecorateRule(siteMeshContext)
        }

        protected processStart(Tag tag) throws IOException {
            return decorateRule.processStart(tag)
        }

        protected void processEnd(Tag tag, data) throws IOException {
            decorateRule.processEnd(tag, data)
        }

        void process(Tag tag) throws IOException {
            def customTag = new CustomTag(tag)
            customTag.addAttribute('decorator', tag.name.replace(':', '/'))
            super.process(customTag)
        }

        void setTagProcessorContext(TagProcessorContext tagProcessorContext) {
            decorateRule.tagProcessorContext = tagProcessorContext
        }
    }

}
