package org.speechforge.cairo.util.rule;

import org.mrcp4j.message.header.ChannelIdentifier;

public class RuleMatch {

    private String _rule;
    private String _tag;

    public RuleMatch(String rule, String tag) {
        _rule = rule;
        _tag = tag;
    }

    public String getRule() {
        return _rule;
    }

    public void setRule(String rule) {
        _rule = rule;
    }

    public String getTag() {
        return _tag;
    }

    public void setTag(String tag) {
        _tag = tag;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof RuleMatch) {
            if ((_rule.equals(((RuleMatch) obj).getRule()))  && 
                ( _tag.equals(((RuleMatch) obj).getTag())) ) {
               return true;
            }
        }
        return false;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return _tag.concat(_rule).hashCode();
    }

    
}
