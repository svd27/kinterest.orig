package ch.passenger.kinterest.util.parser;
/*
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.parboiled.BaseParser;
import org.parboiled.Parboiled;
import org.parboiled.Rule;
import org.parboiled.parserunners.TracingParseRunner;
import org.parboiled.support.Characters;
import org.parboiled.support.ParsingResult;
import org.parboiled.support.StringVar;
import org.parboiled.support.Var;
import scala.collection.Seq;

import java.util.HashMap;
import java.util.Map;
*/

/**
 * Created by svd on 21/01/2014.
 */
/*
public class BoiledFilter extends BaseParser<ObjectNode> {
    final Map<String,String> opMap = new HashMap<String, String>() {
        {
            put("=", "EQ"); put("<", "LT"); put("<=", "LTE");
            put("!=", "NEQ"); put(">", "GT"); put(">=", "GTE");

        }
    };
    final Map<String,String> lopMap = new HashMap<String, String>() {
        {
            put("&&", "AND"); put("||", "OR");
        }
    };
    ObjectMapper om = new ObjectMapper();

    public Rule start() {
        return Sequence(filter(), EOI);
    }

    public Rule filter() {
        return FirstOf(relation(), logical());
    }

    public Rule relation() {
        StringVar target = new StringVar();
        StringVar p = new StringVar();
        return FirstOf(Sequence(name(target), ws(), "->", ws(), filter()), Sequence(qname(target, p), ws(), "<-", ws(), filter(), push(createFrom(target.get(), p.get(), pop()))));
    }

    public Rule logical() {
        StringVar op = new StringVar();
        return Sequence(expression(), Optional(Sequence(ws(), lop(op), ws(), filter()), push(createLogical(op.get(), pop(), pop()))));
        //return FirstOf(Sequence(filter(), ws(), lop(op), ws(), filter(), push(createLogical(op.get(), pop(), pop()))), comparison());
    }

    public Rule expression() {
        return FirstOf(Sequence("(", ws(), filter(), ws(), ")"), comparison());
    }


    public ObjectNode createLogical(String op, ObjectNode o1, ObjectNode o2) {
        ObjectNode on = om.createObjectNode();
        on.set("relation", om.valueToTree(op));
        ArrayNode an = om.createArrayNode();
        an.add(o1);
        an.add(o2);
        on.set("operands", an);
        return on;
    }



    public Rule not() {
        return Sequence("!", ws(), "(", ws(), filter(), ws(), ")", push(createNot(pop())));
    }

    public ObjectNode createNot(ObjectNode filter) {
        ObjectNode on = om.createObjectNode();
        on.set("relation", om.valueToTree("NOT"));
        on.set("operand", om.valueToTree(filter));
        return on;
    }



    public ObjectNode createFrom(String entity,String property, ObjectNode f) {
        ObjectNode on = om.createObjectNode();
        on.set("relation", om.valueToTree("FROM"));
        ObjectNode qn = om.createObjectNode();
        qn.set("entity", om.valueToTree(entity));
        qn.set("property", om.valueToTree(property));
        on.set("qname", qn);
        on.set("filter", f);
        return on;
    }

    public Rule lop(StringVar op) {
        return Sequence(FirstOf("&&", "||"), op.set(lopMap.get(match())));
    }

    public Rule comparison() {
        StringVar name = new StringVar();
        StringVar op = new StringVar();
        Var<Object> value = new Var<Object>();

        return Sequence(name(name), ws(), cop(op),  ws(), value(value), push(createComparison(name.get(), op.get(), value.get())));
    }

    public ObjectNode createComparison(String name, String op, Object value) {
        System.out.println(name+" "+op+" "+value);
        ObjectNode on = om.createObjectNode();
        on.set("property", om.valueToTree(name)); on.set("value", om.valueToTree(value));
        on.set("relation", om.valueToTree(op));
        return on;
    }

    public Rule cop(StringVar op) {
        return Sequence(FirstOf("!=", "<=", "<", ">=", ">", "="), op.set(opMap.get(match())));
    }

    public Rule value(Var<Object> value) {
        return FirstOf(string(value), scalar(value), floats(value));
    }

    public Rule qname(StringVar q, StringVar n) {
        return Sequence(name(q), '.', name(n));
    }
    public Rule name(StringVar name) {
        return Sequence(Sequence(letter(), ZeroOrMore(alphanum())), name.set(match()));
    }
    public Rule alphanum() {
        return FirstOf(letter(), digit());
    }
    public Rule letter() {
        return FirstOf(ucletter(), lcletter());
    }
    public Rule ucletter() {
        return CharRange('A', 'Z');
    }
    public Rule lcletter() {
        return CharRange('a', 'z');
    }
    public Rule string(Var<Object> value) {
        return Sequence('"', Sequence(ZeroOrMore(NoneOf("\"")), value.set(match())), '"');
    }
    public Rule floats(Var<Object> value) {
        return Sequence(Optional(scalar(value)), '.', OneOrMore(digit()), value.set(((Number) (value.get()==null?0L:value.get())).longValue() +Double.parseDouble("."+match())));
    }
    public Rule scalar(Var<Object> value) {
        return Sequence(Optional(FirstOf("+", "-")), OneOrMore(digit()), value.set(Long.parseLong(match())));
    }
    public Rule digit() {
        return CharRange('0', '9');
    }
    public Rule ws() {
        return Optional(AnyOf(" \t\n\r"));
    }

    public static void main(String[] args) {
        ObjectMapper om = new ObjectMapper();
        BoiledFilter parser = Parboiled.createParser(BoiledFilter.class);
        TracingParseRunner<ObjectNode> runner = new TracingParseRunner<ObjectNode>(parser.start());
        ParsingResult<ObjectNode> run = runner.run("AbcAA.gg <- (abra11 >= .2341 && string < 15) || name != \"zaza\"");
        try {
            System.out.println(""+om.writerWithDefaultPrettyPrinter().writeValueAsString(run.resultValue));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }
}
*/
