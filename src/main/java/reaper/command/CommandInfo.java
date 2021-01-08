package reaper.command;

public class CommandInfo{
    public final String text;
    public final String paramText;
    public final String description;
    public final CommandParam[] params;

    public CommandInfo(String text, String paramText, String description){
        this.text = text;
        this.paramText = paramText;
        this.description = description;

        String[] psplit = paramText.split("\\s+");
        if(paramText.length() == 0){
            params = new CommandParam[0];
        }else{
            params = new CommandParam[psplit.length];

            boolean hadOptional = false;

            for(int i = 0; i < params.length; i++){
                String param = psplit[i];

                if(param.length() <= 2){
                    throw new IllegalArgumentException("Malformed param '" + param + "'");
                }

                char l = param.charAt(0), r = param.charAt(param.length() - 1);
                boolean optional, variadic = false;

                if(l == '<' && r == '>'){
                    if(hadOptional){
                        throw new IllegalArgumentException("Can't have non-optional param after optional param!");
                    }
                    optional = false;
                }else if(l == '[' && r == ']'){
                    optional = true;
                }else{
                    throw new IllegalArgumentException("Malformed param '" + param + "'");
                }

                if(optional) hadOptional = true;

                String fname = param.substring(1, param.length() - 1);
                if(fname.endsWith("...")){
                    if(i != params.length - 1){
                        throw new IllegalArgumentException("A variadic parameter should be the last parameter!");
                    }

                    fname = fname.substring(0, fname.length() - 3);
                    variadic = true;
                }

                params[i] = new CommandParam(fname, optional, variadic);
            }
        }
    }
}
